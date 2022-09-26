/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uz.yt.ofd.acr.sample.app.validator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import uz.yt.ofd.acr.sample.app.logger.TLVLogger;
import uz.yt.ofd.codec.message5.AckFileStatus;
import uz.yt.ofd.codec.message5.File;
import uz.yt.ofd.codec.message5.FileType;
import uz.yt.ofd.codec.message5.Message;
import uz.yt.ofd.codec.message5.MessageDecoder;
import uz.yt.ofd.codec.message5.Request;
import uz.yt.ofd.codec.message5.Response;
import uz.yt.ofd.codec.tlv.TLV;
import uz.yt.ofd.codec.tlv.TVS;
import uz.yt.ofd.codec.validator.FiscalSignValidator;

/**
 *
 * @author administrator
 */
public class OnlineFiscalSignValidator implements FiscalSignValidator {

    private final TLVLogger tlvLogger;
    private List<String> serverAddresses;

    protected Random random = new Random();

    static final byte MESSAGE_VERSION = (byte) 5;

    public OnlineFiscalSignValidator(TLVLogger tlvLogger) {
        this.tlvLogger = tlvLogger;
    }

    public void setServerAddresses(List<String> serverAddresses) {
        this.serverAddresses = serverAddresses;
    }

    @Override
    public boolean check(String terminalID, byte[] receiptSeqRaw, byte[] dateTimeRaw, byte[] fiscalSignRaw) throws IOException {

        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(receiptSeqRaw);
        header.write(dateTimeRaw);
        header.write(fiscalSignRaw);

        Request req = new Request(terminalID, new Date(), null, new File[]{
            new File(FileType.VerifyFiscalSignQuery.value, (byte) 1, header.toByteArray(), null, (byte) 0, null)
        });

        byte[] reqRaw;
        try {
            reqRaw = req.encode();
        } catch (Throwable t) {
            throw new IOException(t.getMessage(), t);
        }
        if (tlvLogger != null) {
            try {
                // for debug print                            
                TVS tvs = TLV.decode(TLV.encode(Message.TagRequest, reqRaw));
                tlvLogger.appendDebugLogTLV(tvs, "request", Request.TAG_DESCRIPTIONS, new String[]{"8a.8f"});
            } catch (Throwable t) {

            }
        }

        Message msgReq = new Message(MESSAGE_VERSION, Message.TagRequest, reqRaw);
        Object[] r = trySend(msgReq);
        if (r[0] == null) {
            if (r[1] != null) {
                Map<String, Throwable> errMap = (Map<String, Throwable>) r[1];
                if (errMap.size() > 0) {
                    Throwable t = errMap.values().iterator().next();
                    throw new IOException(t.getMessage(), t);
                }
            }
            throw new IOException("Faied to connect to server");
        }
        Message msgRes = (Message) r[0];

        if (msgRes.getTlvTag() != Message.TagResponse) {
            throw new IOException("bad response message tag");
        }

        if (tlvLogger != null) {
            try {
                // for debug print          
                ByteArrayOutputStream tmp = new ByteArrayOutputStream();
                msgRes.write(tmp);
                MessageDecoder messageDecoder = new MessageDecoder(tmp.toByteArray());
                messageDecoder.decode();
                tlvLogger.appendDebugLogAsText("---Received message---");
                tlvLogger.appendDebugLogAsDumpDesciptor(messageDecoder.getDumpDescriptor());

                // for debug print                            
                TVS tvs = TLV.decode(TLV.encode(msgRes.getTlvTag(), msgRes.getTlvBody()));
                tlvLogger.appendDebugLogTLV(tvs, "response", Response.TAG_DESCRIPTIONS, new String[]{"8b.8e"});

            } catch (Throwable t) {

            }
        }
        Response res;
        try {
            TVS tvs = TLV.decode(msgRes.getTlvBody());
            res = Response.decode(tvs);
            switch (res.getStatusInfo().getStatusCode()) {
                case OK:
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogKeyValue("SUCCESS", "Received " + res.getAckFiles().length + " ack files", 10);
                    }
                    break;
                case OKNotice:
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogKeyValue("SUCCESS", "Notice :" + res.getStatusInfo().getNotice(), 10);
                    }
                    break;
                case RetrySend:
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogKeyValue("WARNING", "Retry send", 10);
                    }
                    throw new IOException("Try later");
                case NotActive:
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogKeyValue("WARNING", "FISCAL DRIVE IS NOT ACTIVE, NOTICE " + res.getStatusInfo().getNotice() + ", REASON CODE: " + res.getStatusInfo().getReasonCode(), 10);
                    }
                    throw new IOException("Try later");
                case NotFound:
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogKeyValue("WARNING", "FISCAL DRIVE IS NOT FOUND", 10);
                    }
                    throw new IOException("Try later");
                case BadMessageSyntax:
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogKeyValue("WARNING", "BAD MESSAGE SYNTAX", 10);
                    }
                    throw new IOException("Try later");
                case BadMessageStruct:
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogKeyValue("WARNING", "BAD MESSAGE STRUCT", 10);
                    }
                    throw new IOException("Try later");
                default:
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogKeyValue("WARNING", "BAD STATUS CODE: " + res.getStatusInfo().getStatusCode(), 10);
                    }
                    throw new IOException("Try later");
            }

        } catch (Throwable t) {
            if (tlvLogger != null) {
                tlvLogger.appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
            }
            throw new IOException("Try later");
        }

        if (res.getAckFiles().length != 1) {
            throw new IOException("Try later");
        }

        return res.getAckFiles()[0].getStatus() == AckFileStatus.Acknowledge;
    }

    private void shuffle(String[] ar) {
        for (int i = ar.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            // Simple swap
            String a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
    }

    private Message send(String serverAddress, Message msgReq) throws Exception {
        if (tlvLogger != null) {
            tlvLogger.appendDebugLogAsText("server address: " + serverAddress);
        }
        String host = serverAddress.substring(0, serverAddress.indexOf(":"));
        int port = Integer.parseInt(serverAddress.substring(serverAddress.indexOf(":") + 1));
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(10000);
            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();

            if (tlvLogger != null) {
                // for debug print          
                ByteArrayOutputStream tmp = new ByteArrayOutputStream();
                msgReq.write(tmp);
                MessageDecoder messageDecoder = new MessageDecoder(tmp.toByteArray());
                messageDecoder.decode();
                tlvLogger.appendDebugLogAsText("---Sent message---");
                tlvLogger.appendDebugLogAsDumpDesciptor(messageDecoder.getDumpDescriptor());
            }
            msgReq.write(os);
            return Message.read(is);
        }

    }

    private Object[] trySend(Message m) {
        Map<String, Throwable> errMap = new HashMap();
        String[] addrs = serverAddresses.toArray(new String[0]);
        Message result = null;

        shuffle(addrs);
        for (String addr : addrs) {
            try {
                result = send(addr, m);
            } catch (Throwable t) {
                errMap.put(addr, t);
                continue;
            }
            break;
        }

        return new Object[]{result, errMap};
    }

}

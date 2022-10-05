/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uz.yt.ofd.acr.sample.app.sender;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import uz.yt.ofd.acr.sample.app.config.SenderConfig;
import uz.yt.ofd.acr.sample.app.logger.TLVLogger;
import uz.yt.ofd.acr.sample.app.storage.SQLiteStorage;
import uz.yt.ofd.acr.sample.app.storage.Storage;
import uz.yt.ofd.codec.HexBin;
import uz.yt.ofd.codec.message5.AckFile;
import uz.yt.ofd.codec.message5.File;
import uz.yt.ofd.codec.message5.FileType;
import uz.yt.ofd.codec.message5.Message;
import uz.yt.ofd.codec.message5.MessageDecoder;
import uz.yt.ofd.codec.message5.Request;
import uz.yt.ofd.codec.message5.Response;
import uz.yt.ofd.codec.message5.SenderInfo;
import uz.yt.ofd.codec.tlv.TLV;
import uz.yt.ofd.codec.tlv.TVS;

/**
 * @author administrator
 */
public class TCPSender implements Sender {

    private final TLVLogger tlvLogger;
    private final SenderConfig senderConfig;
    private final SenderInfo senderInfo;
    private final Storage storage;

    protected SecureRandom random = new SecureRandom();

    static final byte MESSAGE_VERSION = (byte) 5;

    public TCPSender(TLVLogger tlvLogger, SenderConfig senderConfig, Storage storage, SenderInfo senderInfo) {
        this.tlvLogger = tlvLogger;
        this.senderConfig = senderConfig;
        this.storage = storage;
        this.senderInfo = senderInfo;
    }

    @Override
    public void Sync(List<String> serverAddresses) {
        final Map<String, List<Storage.FileInfo>> filesByTerminalID = new HashMap();
        try {

            int[] count = new int[]{0};
            storage.listNewFile(Storage.Table.Send, file -> {
                count[0]++;

                if (!filesByTerminalID.containsKey(file.getTerminalID())) {
                    filesByTerminalID.put(file.getTerminalID(), new LinkedList());
                }
                filesByTerminalID.get(file.getTerminalID()).add(file);

                return count[0] < senderConfig.getNumberOfFilesToSend();
            });
            if (filesByTerminalID.isEmpty()) {
                if (tlvLogger != null) {
                    tlvLogger.appendDebugLog("Nothing to send");
                }
            }
            MAIN:
            for (String teminalID : filesByTerminalID.keySet()) {
                List<File> sendFiles = new LinkedList();
                for (Storage.FileInfo file : filesByTerminalID.get(teminalID)) {
                    byte[] recordId = file.getRecordID().getBytes();
                    sendFiles.add(new File(file.getType().value, file.getVersion(), file.getHeader(), file.getBody(), (byte) 0,recordId ));
                    System.out.println("recordId -> "+Arrays.toString(recordId));
                }
                File[] sentFiles = sendFiles.toArray(new File[0]);
                System.out.println("terminalId -> " + teminalID);
                System.out.println("senderInfo -> name : " + senderInfo.getName() + " sn : " + senderInfo.getSN() + " version : " + senderInfo.getVersion());
                System.out.println("sentFiles size -> " + sentFiles.length);
                File rawFile = sentFiles[0];
                System.out.println("sentFile -> type : " + rawFile.getType() + " tag : " + Arrays.toString(rawFile.getTag()) + " version " + rawFile.getVersion());
                Request req = new Request(teminalID, new Date(), senderInfo, sentFiles);
                byte[] reqRaw = req.encode();

                TVS tvs;
                if (tlvLogger != null) {
                    // for debug print 
                    tvs = TLV.decode(TLV.encode(Message.TagRequest, reqRaw));
                    tlvLogger.appendDebugLogTLV(tvs, "request", Request.TAG_DESCRIPTIONS, new String[]{"8a.8f"});
                }

                Message msgReq = new Message(MESSAGE_VERSION, Message.TagRequest, reqRaw);

                Response res = null;
                String serverAddress = serverAddresses.get(random.nextInt(serverAddresses.size()));
                if (tlvLogger != null) {
                    tlvLogger.appendDebugLogAsText("server address: " + serverAddress);
                }
                String host = serverAddress.substring(0, serverAddress.indexOf(":"));
                int port = Integer.parseInt(serverAddress.substring(serverAddress.indexOf(":") + 1));
                try (Socket socket = new Socket(host, port)) {
                    socket.setSoTimeout(30000);
                    OutputStream os = socket.getOutputStream();
                    InputStream is = socket.getInputStream();

                    // for debug print          
                    ByteArrayOutputStream tmp = new ByteArrayOutputStream();
                    msgReq.write(tmp);
                    MessageDecoder messageDecoder = new MessageDecoder(tmp.toByteArray());
                    messageDecoder.decode();
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogAsText("---Sent message---");
                        tlvLogger.appendDebugLogAsDumpDesciptor(messageDecoder.getDumpDescriptor());
                    }
                    msgReq.write(os);
                    Message msgRes = Message.read(is);

                    if (msgRes.getTlvTag() != Message.TagResponse) {
                        throw new IllegalArgumentException("bad response message tag");
                    }

                    // for debug print          
                    tmp = new ByteArrayOutputStream();
                    msgRes.write(tmp);
                    messageDecoder = new MessageDecoder(tmp.toByteArray());
                    messageDecoder.decode();
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogAsText("---Received message---");
                        tlvLogger.appendDebugLogAsDumpDesciptor(messageDecoder.getDumpDescriptor());
                    }
                    if (tlvLogger != null) {
                        // for debug print                            
                        tvs = TLV.decode(TLV.encode(msgRes.getTlvTag(), msgRes.getTlvBody()));
                        tlvLogger.appendDebugLogTLV(tvs, "response", Response.TAG_DESCRIPTIONS, new String[]{"8b.8e"});
                    }
                    tvs = TLV.decode(msgRes.getTlvBody());
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
                            continue;
                        case RetrySend:
                            if (tlvLogger != null) {
                                tlvLogger.appendDebugLogKeyValue("WARNING", "Retry send", 10);
                            }
                            for (Storage.FileInfo file : filesByTerminalID.get(teminalID)) {
                                storage.postponeSendFile(file);
                            }
                            continue;
                        case NotActive:
                            if (tlvLogger != null) {
                                tlvLogger.appendDebugLogKeyValue("WARNING", "FISCAL DRIVE IS NOT ACTIVE, NOTICE " + res.getStatusInfo().getNotice() + ", REASON CODE: " + res.getStatusInfo().getReasonCode(), 10);
                            }
                            continue;
                        case NotFound:
                            if (tlvLogger != null) {
                                tlvLogger.appendDebugLogKeyValue("WARNING", "FISCAL DRIVE IS NOT FOUND", 10);
                            }
                            for (Storage.FileInfo file : filesByTerminalID.get(teminalID)) {
                                storage.banSendFile(file);
                            }
                            continue;
                        case BadMessageSyntax:
                            if (tlvLogger != null) {
                                tlvLogger.appendDebugLogKeyValue("WARNING", "BAD MESSAGE SYNTAX", 10);
                            }
                            continue;
                        case BadMessageStruct:
                            if (tlvLogger != null) {
                                tlvLogger.appendDebugLogKeyValue("WARNING", "BAD MESSAGE STRUCT", 10);
                            }
                            continue;
                        case TooManyFiles:
                        case TooBigMessage:
                            int limit = senderConfig.getNumberOfFilesToSend();
                            if (limit > 1) {
                                limit--;
                            }
                            senderConfig.setNumberOfFilesToSend(limit);
                            if (tlvLogger != null) {
                                tlvLogger.appendDebugLogKeyValue("WARNING", "TOO MANY FILES OR TOO BIG MESSAGE", 10);
                            }
                            continue;
                        case BadMessageCRC32:
                            if (tlvLogger != null) {
                                tlvLogger.appendDebugLogKeyValue("WARNING", "BAD CRC32", 10);
                            }
                            continue;
                        default:
                            if (tlvLogger != null) {
                                tlvLogger.appendDebugLogKeyValue("WARNING", "BAD STATUS CODE", 10);
                            }
                            continue;

                    }
                } catch (Throwable t) {
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
                    }
                    continue;
                }
                if (res.getAckFiles().length != req.getFiles().length) {
                    if (tlvLogger != null) {
                        tlvLogger.appendDebugLogKeyValue("WARNING", String.format("request files count > response files count: %d != %d", req.getFiles().length, res.getAckFiles().length), 10);
                    }
                    continue;
                }
                for (int i = 0; i < req.getFiles().length; i++) {
                    File requestFile = req.getFiles()[i];
                    AckFile ackFile = res.getAckFiles()[i];

                    System.out.println("Request file tag : " + Arrays.toString(requestFile.getTag()));
                    System.out.println("Ack file tag : " + Arrays.toString(ackFile.getTag()));

                    if (!Arrays.equals(ackFile.getTag(), requestFile.getTag())) {
                        if (tlvLogger != null) {
                            tlvLogger.appendDebugLogKeyValue("WARNING", String.format("request file %d tag %s != response file tag %s", i, HexBin.encode(requestFile.getTag()), HexBin.encode(ackFile.getTag())), 10);
                        }
                        continue MAIN;
                    }
                    FileType ackType = SQLiteStorage.Rec2AckFileMap.get(FileType.find(requestFile.getType()));
                    storage.saveFile(ackType, requestFile.getVersion(), teminalID, null, null, ackFile.getHeader(), ackFile.getBody(), new Date(), new String(requestFile.getTag()), ackFile.getStatus());
                }
            }
        } catch (Throwable t) {
            if (tlvLogger != null) {
                tlvLogger.appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
            }
        }
    }

}

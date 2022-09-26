/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uz.yt.ofd.acr.sample.app.gui;

import com.google.gson.*;
import uz.yt.ofd.acr.sample.app.config.SenderConfig;
import uz.yt.ofd.acr.sample.app.logger.TLVLogger;
import uz.yt.ofd.acr.sample.app.sender.Sender;
import uz.yt.ofd.acr.sample.app.sender.TCPSender;
import uz.yt.ofd.acr.sample.app.storage.SQLiteStorage;
import uz.yt.ofd.acr.sample.app.storage.Storage;
import uz.yt.ofd.acr.sample.app.validator.OnlineFiscalSignValidator;
import uz.yt.ofd.codec.DumpDescriptor;
import uz.yt.ofd.codec.HexBin;
import uz.yt.ofd.codec.Utils;
import uz.yt.ofd.codec.applet.APDUIO;
import uz.yt.ofd.codec.applet.APDUResponse;
import uz.yt.ofd.codec.applet.SW;
import uz.yt.ofd.codec.applet.command.*;
import uz.yt.ofd.codec.applet.decoder.*;
import uz.yt.ofd.codec.applet.dto.*;
import uz.yt.ofd.codec.applet.exception.SWException;
import uz.yt.ofd.codec.crypto.GOST28147Engine;
import uz.yt.ofd.codec.message5.FileType;
import uz.yt.ofd.codec.message5.SenderInfo;
import uz.yt.ofd.codec.receipt7.Receipt;
import uz.yt.ofd.codec.receipt7.*;
import uz.yt.ofd.codec.tlv.OID;
import uz.yt.ofd.codec.tlv.OIDValue;
import uz.yt.ofd.codec.tlv.TLV;
import uz.yt.ofd.codec.tlv.TVS;

import javax.smartcardio.Card;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CardTerminals;
import javax.smartcardio.TerminalFactory;
import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.event.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author administrator
 */
public class TestFrameController implements TLVLogger, SenderConfig {

    protected SecureRandom random = new SecureRandom();

    public TestFrame frame = new TestFrame();
    protected SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Storage storage;

    private final boolean fullDump = "yes".equals(System.getProperty("dump.desciptor.full", "no"));
    private final boolean htmlDump = "yes".equals(System.getProperty("dump.desciptor.html", "no"));

    private boolean experimentalFeatures = false;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().setDateFormat("yyyy-MM-dd HH:mm:ss").registerTypeAdapter(byte[].class, new JsonSerializer<byte[]>() {
        @Override
        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(HexBin.encode(src));
        }

    }).setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

    @Override
    public void appendDebugLog(String message, Object... args) {
        frame.taDebugLog.append(String.format(message, args));
        frame.taDebugLog.setCaretPosition(frame.taDebugLog.getDocument().getLength());
    }

    @Override
    public void appendDebugLogKeyValue(String key, String value, int keyLetfPad) {
        frame.taDebugLog.append(String.format("%-" + keyLetfPad + "s: %s\n", key, value));
        frame.taDebugLog.setCaretPosition(frame.taDebugLog.getDocument().getLength());
    }

    public void appendDebugLogAsJson(Object o) {
        String jsonObject = gson.toJson(o);
        frame.taDebugLog.append(jsonObject + "\n");
        frame.taDebugLog.setCaretPosition(frame.taDebugLog.getDocument().getLength());
    }

    @Override
    public void appendDebugLogAsDumpDesciptor(DumpDescriptor dd) {
        if (fullDump) {
            frame.taDebugLog.append(String.format("%s\n", dd));
        } else {
            frame.taDebugLog.append(String.format("%#s\n", dd));
        }
        if (htmlDump) {
            frame.taDebugLog.append(String.format("---HTML---\n\n%#S\n\n---HTML---\n", dd));
        }
        frame.taDebugLog.setCaretPosition(frame.taDebugLog.getDocument().getLength());
    }

    @Override
    public void appendDebugLogAsText(Object o) {
        frame.taDebugLog.append(o.toString() + "\n");
        frame.taDebugLog.setCaretPosition(frame.taDebugLog.getDocument().getLength());
    }

    @Override
    public void appendDebugLogTLV(TVS tvs, String name, Map<String, String> tagDescriptionMap, String[] arrayOIDs) {
        appendDebugLogAsText("---TLV encoded " + name + "---");
        appendDebugLogAsText(tvs);

        appendDebugLogAsText("---TLV encoded " + name + " OID descriptions---");
        Collection<OID> tl = tvs.tagList();
        Iterator<OID> it = tl.iterator();
        while (it.hasNext()) {
            OID oid = it.next();
            String desc = "";
            if (tagDescriptionMap != null && tagDescriptionMap.containsKey(oid.toString())) {
                desc = " - " + tagDescriptionMap.get(oid.toString());
            }
            appendDebugLogAsText(oid.toString() + desc);
        }
        appendDebugLogAsText("");

        appendDebugLogAsText("---TLV encoded " + name + " OID values---");
        List<OIDValue> tv = tvs.values(arrayOIDs);
        for (OIDValue ov : tv) {
            appendDebugLogAsText(String.format("%#s", ov));
        }
        appendDebugLogAsText("");
    }

    @Override
    public int getNumberOfFilesToSend() {
        return (Integer) frame.spinNumberOfFilesToSend.getValue();
    }

    @Override
    public void setNumberOfFilesToSend(int limit) {
        frame.spinNumberOfFilesToSend.setValue(limit);
    }

    private String receiptItemToHtmlTable(int number, ReceiptItem receiptItem) {
        StringBuilder sb = new StringBuilder();

        sb.append("<tr>");
        sb.append("<td>").append(number).append("</td>");
        sb.append("<td>").append(receiptItem.getName()).append("</td>");
        sb.append("<td>").append(receiptItem.getBarcode()).append("</td>");
        sb.append("<td>").append(receiptItem.getLabel()).append("</td>");
        sb.append("<td>").append(receiptItem.getSpic()).append("</td>");
        sb.append("<td>").append(receiptItem.getUnits()).append("</td>");
        sb.append("<td>").append(receiptItem.getPackageCode()).append("</td>");
        sb.append("<td>").append(receiptItem.getPrice()).append("</td>");
        sb.append("<td>").append(receiptItem.getVatPercent()).append("</td>");
        sb.append("<td>").append(receiptItem.getVat()).append("</td>");
        sb.append("<td>").append(receiptItem.getAmount()).append("</td>");
        sb.append("<td>").append(receiptItem.getDiscount()).append("</td>");
        sb.append("<td>").append(receiptItem.getOther()).append("</td>");
        sb.append("<td>").append(receiptItem.getCommissionInfo() != null ? (receiptItem.getCommissionInfo().getTin() != null ? receiptItem.getCommissionInfo().getTin() : "") : "").append("</td>");
        sb.append("<td>").append(receiptItem.getCommissionInfo() != null ? (receiptItem.getCommissionInfo().getPinfl() != null ? receiptItem.getCommissionInfo().getPinfl() : "") : "").append("</td>");
        sb.append("</tr>");

        return sb.toString();
    }

    private String receiptToHtmlTable(Receipt receipt) {
        StringBuilder sb = new StringBuilder();

        sb.append("<style type='text/css'> table, th, td { border: 1px solid black; border-collapse: collapse; }</style>");
        sb.append("<table>");
        sb.append("<tr>");
        sb.append("<th>Num.</th>");
        sb.append("<th>Name</th>");
        sb.append("<th>Barcode</th>");
        sb.append("<th>Label</th>");
        sb.append("<th>Spic</th>");
        sb.append("<th>Units</th>");
        sb.append("<th>PackageCode</th>");
        sb.append("<th>Price</th>");
        sb.append("<th>VatPercent</th>");
        sb.append("<th>Vat</th>");
        sb.append("<th>Amount</th>");
        sb.append("<th>Discount</th>");
        sb.append("<th>Other</th>");
        sb.append("<th>Commissioner TIN</th>");
        sb.append("<th>Commissioner PINFL</th>");
        sb.append("</tr>");
        List<ReceiptItem> items = receipt.getItems();
        for (int i = 0; i < items.size(); i++) {
            sb.append(receiptItemToHtmlTable(i + 1, items.get(i)));
        }
        sb.append("</table>");

        sb.append("<table>");
        sb.append("<tr>");
        sb.append("<th colspan=2>Receipt</th>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td>ReceivedCash</td>");
        sb.append("<td>").append(receipt.getReceivedCash()).append("</td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td>ReceivedCard</td>");
        sb.append("<td>").append(receipt.getReceivedCard()).append("</td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td>TotalVAT</td>");
        sb.append("<td>").append(receipt.calcTotalVAT()).append("</td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td>Date</td>");
        sb.append("<td>").append(dateFormat.format(receipt.getTime())).append("</td>");
        sb.append("</tr>");

        sb.append("<tr>");
        sb.append("<th colspan=2>Location</th>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td>Longitude</td>");
        sb.append("<td>").append(receipt.getLocation().getLongitude()).append("</td>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<td>Latitude</td>");
        sb.append("<td>").append(receipt.getLocation().getLatitude()).append("</td>");
        sb.append("</tr>");

        if (receipt.getExtraInfo() != null) {
            sb.append("<tr>");
            sb.append("<th colspan=2>ExtraInfo</th>");
            sb.append("</tr>");
            sb.append("<tr>");
            sb.append("<td>TIN</td>");
            sb.append("<td>").append(receipt.getExtraInfo().getTin()).append("</td>");
            sb.append("</tr>");
            sb.append("<tr>");
            sb.append("<td>PINFL</td>");
            sb.append("<td>").append(receipt.getExtraInfo().getPinfl()).append("</td>");
            sb.append("</tr>");
            sb.append("<tr>");
            sb.append("<td>PhoneNumber</td>");
            sb.append("<td>").append(receipt.getExtraInfo().getPhoneNumber()).append("</td>");
            sb.append("</tr>");
            sb.append("<tr>");
            sb.append("<td>CarNumber</td>");
            sb.append("<td>").append(receipt.getExtraInfo().getCarNumber()).append("</td>");
            sb.append("</tr>");
            sb.append("<tr>");
            sb.append("<td>Other</td>");
            sb.append("<td>").append(receipt.getExtraInfo().getOther()).append("</td>");
            sb.append("</tr>");
        }

        if (receipt.getRefundInfo() != null) {
            sb.append("<tr>");
            sb.append("<th colspan=2>RefundInfo</th>");
            sb.append("</tr>");
            sb.append("<tr>");
            sb.append("<td>TerminalID</td>");
            sb.append("<td>").append(receipt.getRefundInfo().getTerminalID()).append("</td>");
            sb.append("</tr>");
            sb.append("<tr>");
            sb.append("<td>ReceiptSeq</td>");
            sb.append("<td>").append(receipt.getRefundInfo().getReceiptSeq()).append("</td>");
            sb.append("</tr>");
            sb.append("<tr>");
            sb.append("<td>DateTime</td>");
            sb.append("<td>").append(receipt.getRefundInfo().getDateTime()).append("</td>");
            sb.append("</tr>");
            sb.append("<tr>");
            sb.append("<td>FiscalSign</td>");
            sb.append("<td>").append(receipt.getRefundInfo().getFiscalSign()).append("</td>");
            sb.append("</tr>");
        }

        sb.append("</table>");
        return sb.toString();
    }

    private void setReceiptJsonTime(Date time) {
        try {
            String receiptJson = frame.taReceiptJson.getText();
            if (receiptJson != null && !receiptJson.isEmpty()) {
                Receipt receipt = gson.fromJson(receiptJson, Receipt.class);
                receipt.setTime(time);
                receiptJson = gson.toJson(receipt);
                frame.taReceiptJson.setText(receiptJson);
            }
        } catch (Throwable t) {
            appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
        }
    }

    private void setReceiptJsonRefundInfo(boolean include, String terminalID, String receiptSeq, String dateTime, String fiscalSign) {
        try {
            String receiptJson = frame.taReceiptJson.getText();
            if (receiptJson != null && !receiptJson.isEmpty()) {
                Receipt receipt = gson.fromJson(receiptJson, Receipt.class);
                if (include) {
                    receipt.setRefundInfo(new RefundInfo(terminalID.trim(), receiptSeq.trim(), dateTime.trim(), fiscalSign.trim()));
                } else {
                    receipt.setRefundInfo(null);
                }
                receiptJson = gson.toJson(receipt);
                frame.taReceiptJson.setText(receiptJson);
            }
        } catch (Throwable t) {
            appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
        }
    }

    private void setReceiptJsonExtraInfo(String tin, String pinfl, String carNumber, String phoneNumber, String qrPaymentId, short qrPaymentProvider, String other) {
        try {
            String receiptJson = frame.taReceiptJson.getText();
            if (receiptJson != null && !receiptJson.isEmpty()) {
                Receipt receipt = gson.fromJson(receiptJson, Receipt.class);
                tin = tin.trim();
                pinfl = pinfl.trim();
                carNumber = carNumber.trim();
                phoneNumber = phoneNumber.trim();
                other = other.trim();
                if (!tin.isEmpty() || !pinfl.isEmpty() || !carNumber.isEmpty() || !phoneNumber.isEmpty() || !qrPaymentId.isEmpty() || qrPaymentProvider != 0 || !other.isEmpty()) {
                    receipt.setExtraInfo(new ExtraInfo(tin, pinfl, carNumber, phoneNumber, qrPaymentId, qrPaymentProvider, other));
                } else {
                    receipt.setExtraInfo(null);
                }
                receiptJson = gson.toJson(receipt);
                frame.taReceiptJson.setText(receiptJson);
            }
        } catch (Throwable t) {
            appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
        }
    }

    static interface Callback {

        void run(APDUIO apduio) throws Exception;
    }

    private void runCardCommand(Object source, Callback callback) {
        appendDebugLog("---" + ((JButton) source).getText() + "---\n");
        try {
            String readerName = (String) frame.cbReader.getSelectedItem();
            if (readerName == null) {
                return;
            }
            appendDebugLogKeyValue("Reader", readerName, 32);
            TerminalFactory tf = TerminalFactory.getDefault();
            CardTerminals cts = tf.terminals();
            List<CardTerminal> ctList = cts.list(CardTerminals.State.CARD_PRESENT);
            for (CardTerminal ct : ctList) {
                if (ct.getName().equals(readerName)) {
                    Card card = ct.connect("T=1");
                    try {
                        appendDebugLogKeyValue("Card ATR", HexBin.encode(card.getATR().getBytes()), 32);
                        appendDebugLogKeyValue("Card ATR Historical Bytes", HexBin.encode(card.getATR().getHistoricalBytes()), 32);
                        appendDebugLog("\n");

                        APDUIO apduio = new CardChannelAPDUIO(card.getBasicChannel(), frame.taDebugLog);
                        APDUResponse response = apduio.transmit(Applet.selectCommand());
                        if (response.getSw() != SW.NO_ERROR.code) {
                            throw new SWException(response.getSw());
                        }

                        try {
                            String appletVersion = new GetVersionCommand().run(apduio, VersionDecoder.class).decode();
                            appendDebugLogKeyValue("AppletVersion", appletVersion, 32);
                            appendDebugLog("\n");

                            if (appletVersion.equals("0323")) {
                                experimentalFeatures = true;
                            } else {
                                experimentalFeatures = false;
                            }

                            callback.run(apduio);

                            appendDebugLog("\n");
                        } finally {
                            apduio.transmit(Applet.deselectCommand());
                        }

                    } finally {
                        card.disconnect(true);
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            appendDebugLogKeyValue("ERROR", t.getClass().getName() + " > " + t.getMessage(), 10);
        }
        appendDebugLog("\n");
    }

    private final SenderInfo senderInfo;
    private final Sender sender;
    private final OnlineFiscalSignValidator fiscalSignValidator;
    SendFilesTableModel sendFilesTableModel = new SendFilesTableModel();
    RecvFilesTableModel recvFilesTableModel = new RecvFilesTableModel();

    void saveZReport(ZReport file) throws Exception {
        storage.saveFile(FileType.ZReport, 1, file.getHeader().getTerminalID(), null, file.getHeader().getCloseTime(), file.getFile(), new byte[0], null);
    }

    void saveReceipt(uz.yt.ofd.codec.applet.dto.Receipt file) throws Exception {
        storage.saveFile(FileType.Receipt, 1, file.getHeader().getTerminalID(), file.getHeader().getReceiptSeq(), null, file.getFile(), new byte[0], null);
    }

    void saveSaleRefundReceipt(SaleRefundReceipt file) throws Exception {
        storage.saveFile(FileType.SaleRefundReceipt, ReceiptCodec.VERSION, file.getTerminalID(), file.getReceiptSeq(), null, file.getRegisterReceiptResponse(), file.getEncyptedReceiptBody(), null);
    }

    void saveAdvanceReceipt(AdvanceReceipt file) throws Exception {
        storage.saveFile(FileType.AdvanceReceipt, ReceiptCodec.VERSION, file.getTerminalID(), null, null, new byte[0], file.getEncodedReceiptBody(), file.getCreateTime());
    }

    void saveCreditReceipt(CreditReceipt file) throws Exception {
        storage.saveFile(FileType.CreditReceipt, ReceiptCodec.VERSION, file.getTerminalID(), null, null, new byte[0], file.getEncodedReceiptBody(), file.getCreateTime());
    }

    public TestFrameController(String version) throws SQLException {
        System.out.println("TestFrameController()");

        storage = new SQLiteStorage();

        frame.tableFiles.setModel(sendFilesTableModel);

        frame.setTitle("AndroidCashRegister-SIM v" + version);
        senderInfo = new SenderInfo("ACR-SIM", "", version);

        sender = new TCPSender(this, this, storage, senderInfo);

        fiscalSignValidator = new OnlineFiscalSignValidator(this);

        Date now = new Date();
        frame.fteZReportDateTime.setText(dateFormat.format(now));
        frame.fteZReportDateTime.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() > 1) {
                    Date now = new Date();
                    frame.fteZReportDateTime.setText(dateFormat.format(now));
                }
                super.mouseClicked(e); //To change body of generated methods, choose Tools | Templates.
            }

        });
        frame.fteReceiptDateTime.setText(dateFormat.format(now));
        frame.fteReceiptDateTime.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() > 1) {
                    Date now = new Date();
                    frame.fteReceiptDateTime.setText(dateFormat.format(now));
                    setReceiptJsonTime(now);
                }
                super.mouseClicked(e); //To change body of generated methods, choose Tools | Templates.
            }

        });

        frame.bClearAll.addActionListener(e -> {
            System.out.println("clear all clicked");
            frame.ftExtraInfoTIN.setText("");
            frame.ftExtraInfoPINFL.setText("");
            frame.ftExtraInfoCarNumber.setText("");
            frame.ftExtraInfoPhoneNumber.setText("");
            frame.tfExtraInfoQRPaymentID.setText("");
            frame.ftExtraInfoQRPaymentProvider.setText("");
            frame.tfExtraInfoOther.setText("");
            setReceiptJsonExtraInfo("", "", "", "", "", (short) 0, "");
        });

        frame.ftExtraInfoTIN.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                setReceiptJsonExtraInfo(frame.ftExtraInfoTIN.getText(), frame.ftExtraInfoPINFL.getText(), frame.ftExtraInfoCarNumber.getText(), frame.ftExtraInfoPhoneNumber.getText(), frame.tfExtraInfoQRPaymentID.getText(), parseShort(frame.ftExtraInfoQRPaymentProvider.getText()), frame.tfExtraInfoOther.getText());
            }
        });

        frame.ftExtraInfoPINFL.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                setReceiptJsonExtraInfo(frame.ftExtraInfoTIN.getText(), frame.ftExtraInfoPINFL.getText(), frame.ftExtraInfoCarNumber.getText(), frame.ftExtraInfoPhoneNumber.getText(), frame.tfExtraInfoQRPaymentID.getText(), parseShort(frame.ftExtraInfoQRPaymentProvider.getText()), frame.tfExtraInfoOther.getText());
            }
        });

        frame.ftExtraInfoCarNumber.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                setReceiptJsonExtraInfo(frame.ftExtraInfoTIN.getText(), frame.ftExtraInfoPINFL.getText(), frame.ftExtraInfoCarNumber.getText(), frame.ftExtraInfoPhoneNumber.getText(), frame.tfExtraInfoQRPaymentID.getText(), parseShort(frame.ftExtraInfoQRPaymentProvider.getText()), frame.tfExtraInfoOther.getText());
            }
        });

        frame.ftExtraInfoPhoneNumber.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                setReceiptJsonExtraInfo(frame.ftExtraInfoTIN.getText(), frame.ftExtraInfoPINFL.getText(), frame.ftExtraInfoCarNumber.getText(), frame.ftExtraInfoPhoneNumber.getText(), frame.tfExtraInfoQRPaymentID.getText(), parseShort(frame.ftExtraInfoQRPaymentProvider.getText()), frame.tfExtraInfoOther.getText());
            }
        });

        frame.tfExtraInfoQRPaymentID.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                setReceiptJsonExtraInfo(frame.ftExtraInfoTIN.getText(), frame.ftExtraInfoPINFL.getText(), frame.ftExtraInfoCarNumber.getText(), frame.ftExtraInfoPhoneNumber.getText(), frame.tfExtraInfoQRPaymentID.getText(), parseShort(frame.ftExtraInfoQRPaymentProvider.getText()), frame.tfExtraInfoOther.getText());
            }
        });

        frame.ftExtraInfoQRPaymentProvider.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                setReceiptJsonExtraInfo(frame.ftExtraInfoTIN.getText(), frame.ftExtraInfoPINFL.getText(), frame.ftExtraInfoCarNumber.getText(), frame.ftExtraInfoPhoneNumber.getText(), frame.tfExtraInfoQRPaymentID.getText(), parseShort(frame.ftExtraInfoQRPaymentProvider.getText()), frame.tfExtraInfoOther.getText());
            }
        });

        frame.tfExtraInfoOther.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                setReceiptJsonExtraInfo(frame.ftExtraInfoTIN.getText(), frame.ftExtraInfoPINFL.getText(), frame.ftExtraInfoCarNumber.getText(), frame.ftExtraInfoPhoneNumber.getText(), frame.tfExtraInfoQRPaymentID.getText(), parseShort(frame.ftExtraInfoQRPaymentProvider.getText().trim()), frame.tfExtraInfoOther.getText().trim());
            }
        });

        frame.cbRefundInfo.addActionListener(e -> setReceiptJsonRefundInfo(frame.cbRefundInfo.isSelected(), frame.ftRefundTerminalID.getText(), frame.ftRefundReceiptSeq.getText(), frame.ftRefundDateTime.getText(), frame.ftRefundFiscalSign.getText()));

        frame.ftRefundTerminalID.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                setReceiptJsonRefundInfo(frame.cbRefundInfo.isSelected(), frame.ftRefundTerminalID.getText(), frame.ftRefundReceiptSeq.getText(), frame.ftRefundDateTime.getText(), frame.ftRefundFiscalSign.getText());
            }
        });

        frame.ftRefundReceiptSeq.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                setReceiptJsonRefundInfo(frame.cbRefundInfo.isSelected(), frame.ftRefundTerminalID.getText(), frame.ftRefundReceiptSeq.getText(), frame.ftRefundDateTime.getText(), frame.ftRefundFiscalSign.getText());
            }
        });

        frame.ftRefundDateTime.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                setReceiptJsonRefundInfo(frame.cbRefundInfo.isSelected(), frame.ftRefundTerminalID.getText(), frame.ftRefundReceiptSeq.getText(), frame.ftRefundDateTime.getText(), frame.ftRefundFiscalSign.getText());
            }
        });

        frame.ftRefundFiscalSign.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                setReceiptJsonRefundInfo(frame.cbRefundInfo.isSelected(), frame.ftRefundTerminalID.getText(), frame.ftRefundReceiptSeq.getText(), frame.ftRefundDateTime.getText(), frame.ftRefundFiscalSign.getText());
            }
        });

        frame.btnGenerateFullReceipt.addActionListener(e -> {
            System.out.println("btnGenerateFullReceipt clicked");
            Receipt receipt = generateReceipt((Integer) frame.spItemsCount.getValue(), (Integer) 12);
            String receiptJson = gson.toJson(receipt);
            frame.taReceiptJson.setText(receiptJson);
            frame.fteReceiptDateTime.setText(dateFormat.format(receipt.getTime()));
            setReceiptJsonExtraInfo(frame.ftExtraInfoTIN.getText(), frame.ftExtraInfoPINFL.getText(), frame.ftExtraInfoCarNumber.getText(), frame.ftExtraInfoPhoneNumber.getText(), frame.tfExtraInfoQRPaymentID.getText(), parseShort(frame.ftExtraInfoQRPaymentProvider.getText()), frame.tfExtraInfoOther.getText());
            setReceiptJsonRefundInfo(frame.cbRefundInfo.isSelected(), frame.ftRefundTerminalID.getText(), frame.ftRefundReceiptSeq.getText(), frame.ftRefundDateTime.getText(), frame.ftRefundFiscalSign.getText());
        });

        frame.btnReload.addActionListener(e -> {
            appendDebugLog("---" + ((JButton) e.getSource()).getText() + "---\n");
            try {
                TerminalFactory tf = TerminalFactory.getDefault();
                CardTerminals cts = tf.terminals();
                List<CardTerminal> ctList = cts.list(CardTerminals.State.CARD_PRESENT);
                DefaultComboBoxModel<String> cbm = new DefaultComboBoxModel();
                for (CardTerminal ct : ctList) {
                    appendDebugLogKeyValue("Found reader", ct.getName(), 32);
                    cbm.addElement(ct.getName());
                }
                frame.cbReader.setModel(cbm);
            } catch (Throwable t) {
                appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
            }
            appendDebugLog("\n");
        });

        frame.btnGetInfo.addActionListener(e -> {
            System.out.println("btnGetInfo clicked");
            runCardCommand(e.getSource(), apduio -> {
                InfoDecoder decoder = new GetInfoCommand().run(apduio, InfoDecoder.class);
                Info info = decoder.decode();
                appendDebugLogAsDumpDesciptor(decoder.getDumpDescriptor());
                appendDebugLogAsJson(info);
            });
        });


        frame.btnGetFiscalMemoryInfo.addActionListener(e -> runCardCommand(e.getSource(), apduio -> {
            System.out.println("btnGetFiscalMemoryInfo info clicked");
            FiscalMemoryInfoDecoder decoder = new GetFiscalMemoryInfoCommand().run(apduio, FiscalMemoryInfoDecoder.class);
            FiscalMemoryInfo info = decoder.decode();
            appendDebugLogAsDumpDesciptor(decoder.getDumpDescriptor());
            appendDebugLogAsJson(info);
        }));

        frame.btnGetZReportCount.addActionListener(e -> runCardCommand(e.getSource(), apduio -> {
            long count = new GetZReportCountCommand().run(apduio, ZReportCountDecoder.class).decode();
            appendDebugLogKeyValue("ZReportCount", String.valueOf(count), 32);
        }));

        frame.btnGetZReportsStats.addActionListener(e -> runCardCommand(e.getSource(), apduio -> {
            ZReportsStatsDecoder decoder = new GetZReportsStatsCommand().run(apduio, ZReportsStatsDecoder.class);
            ZReportsStats info = decoder.decode();
            appendDebugLogAsDumpDesciptor(decoder.getDumpDescriptor());
            appendDebugLogAsJson(info);
        }));

        ActionListener getZReportInfo = e -> runCardCommand(e.getSource(), apduio -> {
            int indexNumber = (Integer) frame.spinZReportIndexNumber.getValue();
            boolean byIndex = (e.getSource().equals(frame.btnGetZReportInfoByIndex));
            ZReportInfoDecoder decoder = new GetZReportInfoCommand((short) indexNumber, byIndex).run(apduio, ZReportInfoDecoder.class);
            ZReportInfo info = decoder.decode();
            appendDebugLogAsDumpDesciptor(decoder.getDumpDescriptor());
            appendDebugLogAsJson(info);
        });

        frame.btnGetZReportInfoByIndex.addActionListener(getZReportInfo);
        frame.btnGetZReportInfoByNumber.addActionListener(getZReportInfo);

        ActionListener queueToSendZReport = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runCardCommand(e.getSource(), new Callback() {
                    @Override
                    public void run(APDUIO apduio) throws Exception {
                        int indexNumber = (Integer) frame.spinZReportIndexNumber.getValue();
                        boolean byIndex = (e.getSource().equals(frame.btnQueueToSendZReportByIndex));
                        ZReportDecoder decoder = new GetZReportCommand((short) indexNumber, byIndex).run(apduio, ZReportDecoder.class);
                        ZReport info = decoder.decode();
                        appendDebugLogAsDumpDesciptor(decoder.getDumpDescriptor());
                        appendDebugLogAsJson(info);
                        if (info.getHeader().getCloseTime() == null) {
                            appendDebugLogKeyValue("WARNING", "Cannot send unclosed ZReport", 10);
                            return;
                        }
                        saveZReport(info);
                    }
                });
            }
        };
        frame.btnQueueToSendZReportByIndex.addActionListener(queueToSendZReport);
        frame.btnQueueToSendZReportByNumber.addActionListener(queueToSendZReport);

        frame.btnOpenZReport.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runCardCommand(e.getSource(), new Callback() {
                    @Override
                    public void run(APDUIO apduio) throws Exception {
                        new OpenCloseZReportCommand(true, dateFormat.parse((String) frame.fteZReportDateTime.getText())).run(apduio, VoidDecoder.class);
                    }
                });
            }
        });

        frame.btnCloseZReport.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runCardCommand(e.getSource(), new Callback() {
                    @Override
                    public void run(APDUIO apduio) throws Exception {
                        new OpenCloseZReportCommand(false, dateFormat.parse((String) frame.fteZReportDateTime.getText())).run(apduio, VoidDecoder.class);
                    }
                });
            }
        });

        frame.btnGetReceiptCount.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runCardCommand(e.getSource(), new Callback() {
                    @Override
                    public void run(APDUIO apduio) throws Exception {
                        long count = new GetReceiptCountCommand().run(apduio, ReceiptCountDecoder.class).decode();
                        appendDebugLogKeyValue("ReceiptCount", String.valueOf(count), 32);
                    }
                });
            }
        });
        frame.btnGetLastRegisteredReceipt.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runCardCommand(e.getSource(), new Callback() {
                    @Override
                    public void run(APDUIO apduio) throws Exception {
                        RegisteredReceiptResponseDecoder decoder = new GetLastRegisteredReceiptResponseCommand().run(apduio, RegisteredReceiptResponseDecoder.class);
                        RegisteredReceiptResponse info = decoder.decode();
                        appendDebugLogAsDumpDesciptor(decoder.getDumpDescriptor());
                        appendDebugLogAsJson(info);
                    }
                });
            }
        });

        frame.btnGetReceiptInfo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runCardCommand(e.getSource(), new Callback() {
                    @Override
                    public void run(APDUIO apduio) throws Exception {
                        int number = (Integer) frame.spinReceiptNumber.getValue();
                        ReceiptInfoDecoder decoder = new GetReceiptInfoCommand((short) number).run(apduio, ReceiptInfoDecoder.class);
                        ReceiptInfo info = decoder.decode();
                        appendDebugLogAsDumpDesciptor(decoder.getDumpDescriptor());
                        appendDebugLogAsJson(info);
                    }
                });
            }
        });

        frame.btnQueueToSendReceiptByNumber.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runCardCommand(e.getSource(), new Callback() {
                    @Override
                    public void run(APDUIO apduio) throws Exception {
                        int number = (Integer) frame.spinReceiptNumber.getValue();
                        ReceiptDecoder decoder = new GetReceiptCommand((short) number).run(apduio, ReceiptDecoder.class);
                        uz.yt.ofd.codec.applet.dto.Receipt info = decoder.decode();
                        appendDebugLogAsDumpDesciptor(decoder.getDumpDescriptor());
                        appendDebugLogAsJson(info);

                        saveReceipt(info);
                    }
                });
            }
        });

        frame.btnRescanReceipts.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runCardCommand(e.getSource(), new Callback() {
                    @Override
                    public void run(APDUIO apduio) throws Exception {
                        Date firstReceiptTime = new RescanReceiptsCommand().run(apduio, DateDecoder.class).decode();
                        if (firstReceiptTime != null) {
                            appendDebugLogKeyValue("FirstReceiptTime", dateFormat.format(firstReceiptTime), 32);
                        }
                    }
                });
            }
        });

        frame.btnQueueToSendFullReceipt.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                String receiptJson = frame.taReceiptJson.getText();
                String receiptTypeName = (String) frame.cbReceiptType.getSelectedItem();

                if (receiptJson != null && !receiptJson.isEmpty() && receiptTypeName != null) {
                    runCardCommand(e.getSource(), new Callback() {
                        @Override
                        public void run(APDUIO apduio) throws Exception {

                            // create receipt
                            Receipt receipt = gson.fromJson(receiptJson, Receipt.class);

                            if (htmlDump) {
                                appendDebugLogAsText(String.format("---HTML---\n\n%s\n\n---HTML---\n", receiptToHtmlTable(receipt)));
                            }

                            ByteArrayOutputStream encodedReceipt = new ByteArrayOutputStream();
                            ByteArrayOutputStream totalBlock = new ByteArrayOutputStream();
                            ByteArrayOutputStream hash = new ByteArrayOutputStream();
                            boolean sale = true;
                            ReceiptCodec.ReceiptType receiptType;
                            switch (receiptTypeName) {
                                case "Sale":
                                    sale = true;
                                    receiptType = ReceiptCodec.ReceiptType.SaleRefund;
                                    break;
                                case "Refund":
                                    sale = false;
                                    receiptType = ReceiptCodec.ReceiptType.SaleRefund;
                                    break;
                                case "Advance":
                                    receiptType = ReceiptCodec.ReceiptType.Advance;
                                    break;
                                case "Credit":
                                    receiptType = ReceiptCodec.ReceiptType.Credit;
                                    break;
                                default:
                                    throw new AssertionError(receiptTypeName);
                            }

                            // get server addresses
                            TableModel model = frame.tableServerAddress.getModel();
                            final List<String> serverAddresses = new LinkedList();
                            for (int i = 0; i < model.getRowCount(); i++) {
                                String sa = (String) model.getValueAt(i, 0);
                                if (sa != null && !sa.trim().isEmpty()) {
                                    serverAddresses.add(sa);
                                }
                            }
                            fiscalSignValidator.setServerAddresses(serverAddresses);

                            // encode receipt
                            ReceiptCodec.encode(receipt, receiptType, sale, encodedReceipt, totalBlock, hash, fiscalSignValidator);
                            byte[] encodedReceiptRaw = encodedReceipt.toByteArray();
                            GOST28147Engine cipher = new GOST28147Engine();
                            if ((encodedReceiptRaw.length % cipher.getBlockSize()) != 0) {
                                throw new IllegalArgumentException(String.format("Bad encoded receipt data size %d, it should be divisible by %d", encodedReceiptRaw.length, cipher.getBlockSize()));
                            }

                            // for debug print
                            TVS tvs = TLV.decode(encodedReceipt.toByteArray());
                            appendDebugLogTLV(tvs, "receipt", ReceiptCodec.TAG_DESCRIPTIONS, new String[]{"8d.8c"});

                            if (receiptType == ReceiptCodec.ReceiptType.SaleRefund) {

                                byte[] totalBlockRaw = totalBlock.toByteArray();
                                byte[] encodedReceiptHash = hash.toByteArray();

                                // for debug print
                                TotalBlockDecoder tbd = new TotalBlockDecoder(totalBlockRaw);
                                TotalBlock tb = tbd.decode();
                                appendDebugLogAsDumpDesciptor(tbd.getDumpDescriptor());
                                appendDebugLogAsJson(tb);
                                appendDebugLogAsText("");

                                DumpDescriptor ddrrd = new DumpDescriptor("REGISTER_RECEIPT_DATA", Utils.append(encodedReceiptHash, totalBlockRaw));
                                ddrrd.readHex("EncodedReceiptHash", 0, encodedReceiptHash.length);
                                ddrrd.readHex("TotalBlock", encodedReceiptHash.length, totalBlockRaw.length);
                                appendDebugLogAsText(ddrrd);

                                RegisteredReceiptResponseDecoder decoder;
                                if (experimentalFeatures) {
                                    new RegisterReceipt3StepCommand(sale, hash.toByteArray(), totalBlock.toByteArray()).run(apduio, VoidDecoder.class);
                                    new SignEncryptRegisteredReceipt3StepCommand().run(apduio, VoidDecoder.class);
//                                    ByteArrayDecoder bad = new GetLastRegisteredReceipt3StepResponseCommand((short) 0, (short) 0).run(apduio, ByteArrayDecoder.class);
//                                    decoder = new RegisteredReceiptResponseDecoder(bad.decode());

                                    int leftSize = RegisteredReceiptResponseDecoder.REGISTERED_RECEIPT_SIZE;
                                    ByteArrayDecoder bad1 = new GetLastRegisteredReceipt3StepResponseCommand((short) 0, (short) 128).run(apduio, ByteArrayDecoder.class);
                                    leftSize -= 128;
                                    ByteArrayDecoder bad2 = new GetLastRegisteredReceipt3StepResponseCommand((short) 128, (short) leftSize).run(apduio, ByteArrayDecoder.class);
                                    decoder = new RegisteredReceiptResponseDecoder(Utils.append(bad1.decode(), bad2.decode()));
                                } else {
                                    decoder = new RegisterReceiptCommand(sale, hash.toByteArray(), totalBlock.toByteArray())
                                            .run(apduio, RegisteredReceiptResponseDecoder.class);
                                }

                                RegisteredReceiptResponse info = decoder.decode();
                                appendDebugLogAsDumpDesciptor(decoder.getDumpDescriptor());
                                appendDebugLogAsJson(info);

                                // encrypt encoded receipt 
                                cipher.init(true, cipher.getSBox("D-A"), info.getKey());
                                int blocks = encodedReceiptRaw.length / cipher.getBlockSize();
                                byte[] file = new byte[encodedReceiptRaw.length];
                                for (int bn = 0; bn < blocks; bn++) {
                                    cipher.processBlock(encodedReceiptRaw, bn * cipher.getBlockSize(), file, bn * cipher.getBlockSize());
                                }

                                SaleRefundReceipt saleRefundReceipt = new SaleRefundReceipt();
                                saleRefundReceipt.setTerminalID(info.getTerminalID());
                                saleRefundReceipt.setReceiptSeq(info.getReceiptSeq());
                                saleRefundReceipt.setTransactionTime(info.getTransactionTime());
                                saleRefundReceipt.setRegisterReceiptResponse(info.getReceiptInfo());
                                saleRefundReceipt.setEncyptedReceiptBody(file);

                                saveSaleRefundReceipt(saleRefundReceipt);

                            } else {
                                InfoDecoder decoder = new GetInfoCommand().run(apduio, InfoDecoder.class);
                                Info info = decoder.decode();
                                if (receiptType == ReceiptCodec.ReceiptType.Advance) {
                                    AdvanceReceipt advanceReceipt = new AdvanceReceipt();
                                    advanceReceipt.setTerminalID(info.getTerminalID());
                                    advanceReceipt.setCreateTime(receipt.getTime());
                                    advanceReceipt.setEncodedReceiptBody(encodedReceiptRaw);

                                    saveAdvanceReceipt(advanceReceipt);
                                }
                                if (receiptType == ReceiptCodec.ReceiptType.Credit) {
                                    CreditReceipt creditReceipt = new CreditReceipt();
                                    creditReceipt.setTerminalID(info.getTerminalID());
                                    creditReceipt.setCreateTime(receipt.getTime());
                                    creditReceipt.setEncodedReceiptBody(encodedReceiptRaw);

                                    saveCreditReceipt(creditReceipt);
                                }
                            }
                        }
                    });
                }

            }
        });

        ActionListener listFiles = e -> {
            appendDebugLog("---" + ((JButton) e.getSource()).getText() + "---\n");
            try {
                final FilesTableModel model;
                Storage.Table table;
                if ((e.getSource().equals(frame.btnListSendFiles))) {
                    model = sendFilesTableModel;
                    table = Storage.Table.Send;
                } else {
                    table = Storage.Table.Recv;
                    model = recvFilesTableModel;
                }
                model.clear();
                frame.tableFiles.setModel(model);
                storage.listFileInfo(table, new Storage.Callback() {
                    @Override
                    public boolean next(Storage.FileInfo file) {
                        model.add(file);
                        return true;
                    }
                });
            } catch (Throwable t) {
                appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
            }
            appendDebugLogAsText("");
        };

        frame.btnListSendFiles.addActionListener(listFiles);
        frame.btnListRecvFiles.addActionListener(listFiles);

        frame.btnSend.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                appendDebugLog("---" + ((JButton) e.getSource()).getText() + "---\n");

                TableModel model = frame.tableServerAddress.getModel();
                final List<String> serverAddresses = new LinkedList();
                for (int i = 0; i < model.getRowCount(); i++) {
                    String sa = (String) model.getValueAt(i, 0);
                    if (sa != null && !sa.trim().isEmpty()) {
                        serverAddresses.add(sa);
                    }
                }
                sender.Sync(serverAddresses);
            }
        });

        frame.btnGetAckCount.addActionListener(e -> {
            appendDebugLog("---" + ((JButton) e.getSource()).getText() + "---\n");
            try {
                final Map<String, Integer> byTerminalID = new HashMap();
                storage.listFileByState(Storage.Table.Recv, Storage.State.New, new Storage.Callback() {
                    @Override
                    public boolean next(Storage.FileInfo file) {
                        if (!byTerminalID.containsKey(file.getTerminalID())) {
                            byTerminalID.put(file.getTerminalID(), 0);
                        }
                        byTerminalID.put(file.getTerminalID(), byTerminalID.get(file.getTerminalID()) + 1);
                        return true;
                    }
                });
                for (String terminalID : byTerminalID.keySet()) {
                    appendDebugLogAsText(String.format("%s: %d", terminalID, byTerminalID.get(terminalID)));
                }
            } catch (Throwable t) {
                appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
            }
            appendDebugLogAsText("");
        });

        frame.btnAck.addActionListener(e -> {
            try {

                runCardCommand(e.getSource(), apduio -> {
                    InfoDecoder decoder = new GetInfoCommand().run(apduio, InfoDecoder.class);
                    Info info = decoder.decode();
                    final String terminalID = info.getTerminalID();

                    final Map<String, Storage.State> states = new HashMap();
                    storage.listFileByState(Storage.Table.Recv, Storage.State.New, new Storage.Callback() {
                        @Override
                        public boolean next(Storage.FileInfo file) {
                            try {
                                if (terminalID.equals(file.getTerminalID())) {
                                    switch (file.getType()) {
                                        case SaleRefundReceiptAck:
                                            try {
                                                new AckReceiptCommand(file.getBody()).run(apduio, VoidDecoder.class);
                                                appendDebugLogAsText(String.format("SALE/REFUND RECEIPT ACK %s: %s", terminalID, HexBin.encode(file.getHeader(), 8, 8)));
                                                states.put(file.getRecordID(), Storage.State.Ack);
                                            } catch (Throwable t) {
                                                if (t instanceof SWException) {
                                                    SWException sw = (SWException) t;
                                                    if (sw.getSw() == SW.ERROR_RECEIPT_NOT_FOUND) {
                                                        // probably it was acked before
                                                        states.put(file.getRecordID(), Storage.State.Ack);
                                                        appendDebugLogAsText(String.format("SALE/REFUND RECEIPT ACK SW ERROR %s: %s => %04x - RECEIPT_NOT_FOUND", terminalID, HexBin.encode(file.getHeader(), 8, 8), sw.getSw()));
                                                    } else {
                                                        states.put(file.getRecordID(), Storage.State.AckError);
                                                        appendDebugLogAsText(String.format("SALE/REFUND RECEIPT ACK SW ERROR %s: %s => %04x", terminalID, HexBin.encode(file.getHeader(), 8, 8), sw.getSw()));
                                                    }
                                                } else {
                                                    appendDebugLogAsText(String.format("SALE/REFUND RECEIPT ACK FAIL %s: %s => %s", terminalID, HexBin.encode(file.getHeader(), 8, 8), t.getMessage()));
                                                }
                                            }
                                            break;
                                        case ZReportAck:
                                            try {
                                                new AckZReportCommand(file.getBody()).run(apduio, VoidDecoder.class);
                                                appendDebugLogAsText(String.format("ZREPORT ACK %s: %s", terminalID, HexBin.encode(file.getHeader(), 8, 8)));
                                                states.put(file.getRecordID(), Storage.State.Ack);
                                            } catch (Throwable t) {
                                                if (t instanceof SWException) {
                                                    SWException sw = (SWException) t;
                                                    if (sw.getSw() == SW.ERROR_RECEIPT_NOT_FOUND) {
                                                        // probably it was acked before
                                                        states.put(file.getRecordID(), Storage.State.Ack);
                                                        appendDebugLogAsText(String.format("ZREPORT ACK SW ERROR %s: %s => %04x - RECEIPT_NOT_FOUND", terminalID, HexBin.encode(file.getHeader(), 8, 8), sw.getSw()));
                                                    } else {
                                                        states.put(file.getRecordID(), Storage.State.AckError);
                                                        appendDebugLogAsText(String.format("ZREPORT ACK SW ERROR %s: %s => %04x", terminalID, HexBin.encode(file.getHeader(), 8, 8), sw.getSw()));
                                                    }
                                                } else {
                                                    appendDebugLogAsText(String.format("ZREPORT ACK FAIL %s: %s => %s", terminalID, HexBin.encode(file.getHeader(), 8, 8), t.getMessage()));
                                                }
                                            }
                                            break;
                                        case ReceiptAck:
                                            try {
                                                new AckReceiptCommand(file.getBody()).run(apduio, VoidDecoder.class);
                                                appendDebugLogAsText(String.format("RECEIPT ACK %s: %s", terminalID, HexBin.encode(file.getHeader(), 8, 8)));
                                                states.put(file.getRecordID(), Storage.State.Ack);
                                            } catch (Throwable t) {
                                                if (t instanceof SWException) {
                                                    SWException sw = (SWException) t;
                                                    if (sw.getSw() == SW.ERROR_RECEIPT_NOT_FOUND) {
                                                        // probably it was acked before
                                                        states.put(file.getRecordID(), Storage.State.Ack);
                                                        appendDebugLogAsText(String.format("RECEIPT ACK SW ERROR %s: %s => %04x - RECEIPT_NOT_FOUND", terminalID, HexBin.encode(file.getHeader(), 8, 8), sw.getSw()));
                                                    } else {
                                                        states.put(file.getRecordID(), Storage.State.AckError);
                                                        appendDebugLogAsText(String.format("RECEIPT ACK SW ERROR %s: %s => %04x", terminalID, HexBin.encode(file.getHeader(), 8, 8), sw.getSw()));
                                                    }
                                                } else {
                                                    appendDebugLogAsText(String.format("RECEIPT ACK FAIL %s: %s => %s", terminalID, HexBin.encode(file.getHeader(), 8, 8), t.getMessage()));
                                                }
                                            }
                                            break;
                                        case AdvanceReceiptAck:
                                            appendDebugLogAsText(String.format("ADVANCE RECEIPT ACK %s: %s", terminalID, "OK"));
                                            states.put(file.getRecordID(), Storage.State.Ack);
                                            break;
                                        case CreditReceiptAck:
                                            appendDebugLogAsText(String.format("CREDIT RECEIPT ACK %s: %s", terminalID, "OK"));
                                            states.put(file.getRecordID(), Storage.State.Ack);
                                            break;
                                        default:
                                    }
                                    appendDebugLogAsText("");
                                }
                            } catch (Throwable t) {
                                appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
                            }
                            return true;
                        }
                    });
                    storage.setFilesStates(states);
                });
            } catch (Throwable t) {
                appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
            }
            appendDebugLogAsText("");
        });

        frame.btnCleanDB.addActionListener(ae -> {
            try {
                storage.clean();
            } catch (Throwable t) {
                appendDebugLogKeyValue("ERROR", t.getMessage(), 10);
            }
        });

        frame.btnReload.doClick();
    }

    public ReceiptItem generateItem(char nameChar, int vatPersent) {
        int nameLen = (Math.abs(random.nextInt()) % 80) + 4;
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < nameLen; i++) {
            name.append(nameChar);
        }
        StringBuilder barcode = new StringBuilder();
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            barcode.append((Math.abs(random.nextLong()) % 10));
            label.append((Math.abs(random.nextLong()) % 10));
        }
        StringBuilder pacode = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            pacode.append((Math.abs(random.nextLong()) % 10));
        }

        String spic = String.valueOf(Math.abs(random.nextLong()) % 100000000000000000l);
        long units = (long) ((Math.abs(random.nextLong()) % 1000000000) + 1000);
        long price = (Math.abs(random.nextLong()) % 1000000) + 10000;
        long vat = (long) ((price * vatPersent) / (100 + vatPersent));
        long amount = (Math.abs(random.nextLong()) % 4) * 1000 + 1000;
        long discount = 0;
        long other = 0;
        switch (Math.abs(random.nextInt()) % 4) {
            case 0:
                discount = 0;
                other = 0;
                break;
            case 1:
                discount = price;
                other = 0;
                break;
            case 2:
                discount = 0;
                other = price;
                break;
            case 3:
                discount = price / 3;
                other = price / 3;
                break;
        }
        String comtin = null;
        String compinfl = null;
        if ((Math.abs(random.nextInt()) % 4) == 0) {
            comtin = String.valueOf((Math.abs(random.nextLong()) % 100000000) + 200000000);
            compinfl = String.valueOf((Math.abs(random.nextLong()) % 10000000000000l) + 30000000000000l);
            switch (Math.abs(random.nextInt()) % 3) {
                case 0:
                    compinfl = "";
                case 1:
                    comtin = "";
            }
        }
        return new ReceiptItem(name.toString(), barcode.toString(), label.toString(), spic, units, pacode.toString(), price, (short) vatPersent, vat, amount, discount, other, (comtin == null && compinfl == null) ? null : new CommissionInfo(comtin, compinfl));
    }

    public Receipt generateReceipt(Integer count, Integer vatPersent) {
        long totalCash = 0;
        long totalCard = 0;
        LinkedList<ReceiptItem> items = new LinkedList();
        for (int i = 0; i < count; i++) {
            ReceiptItem it = generateItem((char) (Character.valueOf('A') + i), vatPersent);
            if ((Math.abs(random.nextInt()) % 10) > 8) {
                totalCash += it.getPrice() - it.getDiscount() - it.getOther();
            } else {
                totalCard += it.getPrice() - it.getDiscount() - it.getOther();
            }
            items.add(it);
        }
        int MAX_ALLOWED_DIFF = 10000;
        long diff = (long) (Math.abs(random.nextInt()) % (MAX_ALLOWED_DIFF + 1));
        int op = Math.abs(random.nextInt()) % 3;
        if (op == 1) {
            if (totalCard >= diff) {
                totalCard -= diff;
            } else if (totalCash >= diff) {
                totalCash -= diff;
            }
        }
        if (op == 2) {
            if (totalCard != 0) {
                totalCard += diff;
            } else if (totalCash != 0) {
                totalCash += diff;
            }
        }
        double lon = 69.218415f;
        double lat = 41.295800f;
        double dlat = (2 * random.nextDouble() - 1) / 1e3;
        double dlon = (2 * random.nextDouble() - 1) / 1e3;
        Location location = new Location(lat + dlat, lon + dlon);
        Receipt receipt = new Receipt(items, totalCash, totalCard, new Date(), location);
        return receipt;
    }

    private static short parseShort(String s) {
        try {
            return Short.parseShort(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    public JFrame show() {
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);

        return frame;
    }
}

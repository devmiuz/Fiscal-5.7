/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uz.yt.ofd.acr.sample.app.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import uz.yt.ofd.acr.sample.app.storage.Storage.State;
import uz.yt.ofd.codec.message5.AckFileStatus;
import uz.yt.ofd.codec.message5.FileType;

/**
 *
 * @author administrator
 */
public class SQLiteStorage implements Storage {

    public static final Map<FileType, FileType> Rec2AckFileMap = new HashMap();

    public static final Map<FileType, FileType> Ack2RecFileMap = new HashMap();

    static final Map<FileType, Table> typeTableMap = new HashMap();

    static {
        Rec2AckFileMap.put(FileType.SaleRefundReceipt, FileType.SaleRefundReceiptAck);
        Rec2AckFileMap.put(FileType.ZReport, FileType.ZReportAck);
        Rec2AckFileMap.put(FileType.Receipt, FileType.ReceiptAck);
        Rec2AckFileMap.put(FileType.AdvanceReceipt, FileType.AdvanceReceiptAck);
        Rec2AckFileMap.put(FileType.CreditReceipt, FileType.CreditReceiptAck);

        Ack2RecFileMap.put(FileType.SaleRefundReceiptAck, FileType.SaleRefundReceipt);
        Ack2RecFileMap.put(FileType.ZReportAck, FileType.ZReport);
        Ack2RecFileMap.put(FileType.ReceiptAck, FileType.Receipt);
        Ack2RecFileMap.put(FileType.AdvanceReceiptAck, FileType.AdvanceReceipt);
        Ack2RecFileMap.put(FileType.CreditReceiptAck, FileType.CreditReceipt);

        typeTableMap.put(FileType.SaleRefundReceipt, Table.Send);
        typeTableMap.put(FileType.ZReport, Table.Send);
        typeTableMap.put(FileType.Receipt, Table.Send);
        typeTableMap.put(FileType.AdvanceReceipt, Table.Send);
        typeTableMap.put(FileType.CreditReceipt, Table.Send);
        typeTableMap.put(FileType.SaleRefundReceiptAck, Table.Recv);
        typeTableMap.put(FileType.ZReportAck, Table.Recv);
        typeTableMap.put(FileType.ReceiptAck, Table.Recv);
        typeTableMap.put(FileType.AdvanceReceiptAck, Table.Recv);
        typeTableMap.put(FileType.CreditReceiptAck, Table.Recv);

    }
    // not thread safe
    protected SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final static int QUERY_TIMEOUT_SEC = 30;

    private static final String[] CREATE_TABLES = new String[]{
        "create table send ("
        + "rec_id text, "
        + "type integer, "
        + "version integer, "
        + "terminal_id text, "
        + "receipt_seq text, "
        + "close_time text, "
        + "header blob, "
        + "body blob, "
        + "state integer, "
        + "ack_status integer, "
        + "create_time text)",
        "create table recv ("
        + "rec_id text, "
        + "type integer, "
        + "version integer, "
        + "terminal_id text, "
        + "receipt_seq text, "
        + "close_time text, "
        + "header blob, "
        + "body blob, "
        + "state integer, "
        + "ack_status integer, "
        + "create_time text)"
    };

    java.io.File dbFile;

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getPath());
    }

    public SQLiteStorage() throws SQLException {
        dbFile = new java.io.File(System.getProperty("storage", "storage.sqlite"));
        if (!dbFile.exists()) {
            try (Connection connection = getConnection()) {
                try (Statement st = connection.createStatement()) {
                    st.setQueryTimeout(QUERY_TIMEOUT_SEC);
                    for (String ct : CREATE_TABLES) {
                        st.executeUpdate(ct);
                    }
                }
            }
        }
    }

    @Override
    public void saveFile(FileType type, int version, String terminalID, Long receiptSeq, Date closeTime, byte[] header, byte[] body, Date createTime, String recordID, AckFileStatus ackStatus) throws Exception {
        Table table = typeTableMap.get(type);
        UUID uuid = UUID.randomUUID();
        String newRecordID = uuid.toString().replaceAll("-", "");
        try (Connection connection = getConnection()) {
            try (PreparedStatement pst = connection.prepareStatement("insert into " + table.value + " values($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)")) {
                pst.setQueryTimeout(QUERY_TIMEOUT_SEC);
                pst.setString(1, recordID == null ? newRecordID : recordID);
                pst.setInt(2, type.value);
                pst.setInt(3, version);
                pst.setString(4, terminalID);
                pst.setString(5, receiptSeq == null ? "" : String.valueOf(receiptSeq));
                pst.setString(6, closeTime == null ? "" : dateFormat.format(closeTime));
                pst.setBytes(7, header);
                pst.setBytes(8, body);
                pst.setInt(9, ackStatus == AckFileStatus.Acknowledge ? State.New.value : State.Bad.value);
                pst.setInt(10, ackStatus.value);
                pst.setString(11, createTime == null ? dateFormat.format(new Date()) : dateFormat.format(createTime));
                pst.executeUpdate();
            }
            if (table == Table.Recv && recordID != null) {
                try (PreparedStatement pst = connection.prepareStatement("update send set state=$1,ack_status=$2 where rec_id=$4")) {
                    pst.setQueryTimeout(QUERY_TIMEOUT_SEC);
                    pst.setInt(1, ackStatus == AckFileStatus.Acknowledge ? State.Sent.value : State.Bad.value);
                    pst.setInt(2, ackStatus.value);
                    pst.setString(3, recordID);
                    pst.executeUpdate();
                }
            }
        }

    }

    @Override
    public void saveFile(FileType type, int version, String terminalID, Long receiptSeq, Date closeTime, byte[] header, byte[] body, Date createTime) throws Exception {
        saveFile(type, version, terminalID, receiptSeq, closeTime, header, body, createTime, null, AckFileStatus.Acknowledge);
    }

    @Override
    public int listFileInfo(Table table, Callback callback) throws Exception {
        int count = 0;
        try (Connection connection = getConnection()) {
            String sql;
            if (table == Table.Send) {
                sql = "select rec_id, type, version, terminal_id, receipt_seq, close_time, state, create_time from " + table.value;
            } else {
                sql = "select rec_id, type, version, terminal_id, receipt_seq, close_time, state, ack_status, header, create_time from " + table.value;
            }
            try (PreparedStatement pst = connection.prepareStatement(sql)) {
                ResultSet rs = pst.executeQuery();
                while (rs.next()) {
                    count++;
                    Storage.FileInfo fileInfo;
                    if (table == Table.Send) {
                        fileInfo = new FileInfoImpl(rs);
                    } else {
                        fileInfo = new AckFileInfoImpl(rs);
                    }
                    if (!callback.next(fileInfo)) {
                        return count;
                    }
                }
                rs.close();
            }
        }
        return count;
    }

    @Override
    public int listFileByState(Table table, State state, Callback callback) throws Exception {
        int count = 0;
        try (Connection connection = getConnection()) {
            String sql;
            if (table == Table.Send) {
                sql = "select rec_id, type, version, terminal_id, receipt_seq, close_time, state, header, body, create_time from " + table.value + " where state=$1";
            } else {
                sql = "select rec_id, type, version, terminal_id, receipt_seq, close_time, state, header, body, ack_status, create_time from " + table.value + " where state=$1";
            }
            try (PreparedStatement pst = connection.prepareStatement(sql)) {
                pst.setInt(1, state.value);
                ResultSet rs = pst.executeQuery();
                while (rs.next()) {
                    count++;
                    Storage.FileInfo fileInfo;
                    if (table == Table.Send) {
                        fileInfo = new FileImpl(rs);
                    } else {
                        fileInfo = new AckFileImpl(rs);
                    }
                    if (!callback.next(fileInfo)) {
                        return count;
                    }
                }
                rs.close();
            }
        }
        return count;
    }

    @Override
    public int listNewFile(Table table, Callback callback) throws Exception {
        int count = 0;
        try (Connection connection = getConnection()) {
            try (PreparedStatement pst = connection.prepareStatement("select rec_id, type, version, terminal_id, receipt_seq, close_time, state, header, body, create_time from " + table.value + " where state=$1")) {
                pst.setInt(1, State.New.value);
                ResultSet rs = pst.executeQuery();
                while (rs.next()) {
                    count++;
                    Storage.FileInfo fileInfo;
                    if (table == Table.Send) {
                        fileInfo = new FileImpl(rs);
                    } else {
                        fileInfo = new AckFileImpl(rs);
                    }
                    if (!callback.next(fileInfo)) {
                        return count;
                    }
                }
                rs.close();
            }
        }
        return count;
    }

    private State postponeStateHandler(State currentState) {
        switch (currentState) {
            case Postponed:
                return State.Postponed1;
            case Postponed1:
                return State.Postponed2;
            case Postponed2:
                return State.Postponed3;
            case Postponed3:
                return State.Postponed4;
            case Postponed4:
                return State.Postponed5;
            case Postponed5:
                return State.Postponed6;
            case Postponed6:
                return State.Postponed7;
            case Postponed7:
                return State.Postponed8;
            case Postponed8:
                return State.Postponed9;
            case Postponed9:
                return State.Rejected;
            default:
                return State.Postponed;
        }
    }

    @Override
    public void postponeSendFile(FileInfo file) throws Exception {
        try (Connection connection = getConnection()) {
            try (PreparedStatement pst = connection.prepareStatement("update send set state=$1 where rec_id=$2")) {
                pst.setInt(1, postponeStateHandler(file.getState()).value);
                pst.setString(2, file.getRecordID());
                pst.executeUpdate();
            }
        }
    }

    @Override
    public void banSendFile(FileInfo file) throws Exception {
        try (Connection connection = getConnection()) {
            try (PreparedStatement pst = connection.prepareStatement("update send set state=$1 where rec_id=$2")) {
                pst.setInt(1, State.Banned.value);
                pst.setString(2, file.getRecordID());
                pst.executeUpdate();
            }
        }
    }

    @Override
    public void setFilesStates(Map<String, State> states) throws Exception {
        try (Connection connection = getConnection()) {
            for (String recordID : states.keySet()) {
                State state = states.get(recordID);
                try (PreparedStatement pst = connection.prepareStatement("update recv set state=$1 where rec_id=$2")) {
                    pst.setInt(1, state.value);
                    pst.setString(2, recordID);
                    pst.executeUpdate();
                }
                try (PreparedStatement pst = connection.prepareStatement("update send set state=$1 where rec_id=$2")) {
                    pst.setInt(1, state.value);
                    pst.setString(2, recordID);
                    pst.executeUpdate();
                }
            }
        }
    }

    @Override
    public void clean() throws Exception {
        try (Connection connection = getConnection()) {
            try (PreparedStatement pst = connection.prepareStatement("delete from send where state=$1")) {
                pst.setInt(1, State.Ack.value);
                pst.executeUpdate();
            }
            try (PreparedStatement pst = connection.prepareStatement("delete from recv where state=$1")) {
                pst.setInt(1, State.Ack.value);
                pst.executeUpdate();
            }
        }
    }

}

class FileInfoImpl implements Storage.FileInfo {

    final String recordID;

    final FileType type;

    final byte version;

    final String terminalID;

    final String receiptSeq;

    final String closeTime;

    final State state;

    final String createTime;

    public FileInfoImpl(ResultSet rs) throws SQLException {
        this.recordID = rs.getString("rec_id");
        this.type = FileType.find((byte) rs.getInt("type"));
        this.version = (byte) rs.getInt("version");
        this.terminalID = rs.getString("terminal_id");
        this.receiptSeq = rs.getString("receipt_seq");
        this.closeTime = rs.getString("close_time");
        this.state = State.find(rs.getInt("state"));
        this.createTime = rs.getString("create_time");
    }

    @Override
    public String getRecordID() {
        return recordID;
    }

    @Override
    public FileType getType() {
        return type;
    }

    @Override
    public byte getVersion() {
        return version;
    }

    @Override
    public String getTerminalID() {
        return terminalID;
    }

    @Override
    public String getReceiptSeq() {
        return receiptSeq;
    }

    @Override
    public String getCloseTime() {
        return closeTime;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public String getCreateTime() {
        return createTime;
    }

    @Override
    public byte[] getHeader() {
        return null;
    }

    @Override
    public byte[] getBody() {
        return null;
    }

    @Override
    public AckFileStatus getAckFileStatus() {
        return null;
    }

    @Override
    public String getTraceFileName() {
        return null;
    }

}

class AckFileInfoImpl extends FileInfoImpl {

    final AckFileStatus ackFileStatus;

    final String traceFileName;

    public AckFileInfoImpl(ResultSet rs) throws SQLException {
        super(rs);
        this.ackFileStatus = AckFileStatus.find((byte) rs.getInt("ack_status"));
        this.traceFileName = ackFileStatus != AckFileStatus.Acknowledge ? new String(rs.getBytes("header")) : "";
    }

    @Override
    public AckFileStatus getAckFileStatus() {
        return ackFileStatus;
    }

    @Override
    public String getTraceFileName() {
        return traceFileName;
    }

}

class FileImpl extends FileInfoImpl {

    final byte[] header;

    final byte[] body;

    public FileImpl(ResultSet rs) throws SQLException {
        super(rs);
        this.header = rs.getBytes("header");
        this.body = rs.getBytes("body");
    }

    @Override
    public byte[] getBody() {
        return body;
    }

    @Override
    public byte[] getHeader() {
        return header;
    }

}

class AckFileImpl extends AckFileInfoImpl {

    final byte[] header;

    final byte[] body;

    public AckFileImpl(ResultSet rs) throws SQLException {
        super(rs);
        this.header = rs.getBytes("header");
        this.body = rs.getBytes("body");
    }

    @Override
    public byte[] getBody() {
        return body;
    }

    @Override
    public byte[] getHeader() {
        return header;
    }

}

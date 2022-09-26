/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uz.yt.ofd.acr.sample.app.storage;

import java.util.Date;
import java.util.Map;
import uz.yt.ofd.codec.message5.AckFileStatus;
import uz.yt.ofd.codec.message5.FileType;

/**
 *
 * @author administrator
 */
public interface Storage {

    public static enum Table {
        Send("send"),
        Recv("recv");

        public final String value;

        Table(String value) {
            this.value = value;
        }
    }

    public static enum State {
        New(0),
        Sent(100),
        Bad(50),
        Banned(60),
        Postponed(80),
        Postponed1(81),
        Postponed2(82),
        Postponed3(83),
        Postponed4(84),
        Postponed5(85),
        Postponed6(86),
        Postponed7(87),
        Postponed8(88),
        Postponed9(89),
        Rejected(99),
        Ack(110),
        AckError(105);

        public final int value;

        State(int value) {
            this.value = value;
        }

        public static State find(int value) {
            for (State s : values()) {
                if (s.value == value) {
                    return s;
                }
            }
            return null;
        }

    }
    
    static interface FileInfo {

        String getRecordID();

        FileType getType();
        
        byte getVersion();

        String getTerminalID();

        String getReceiptSeq();

        String getCloseTime();

        State getState();

        String getCreateTime();

        byte[] getHeader();

        byte[] getBody();

        AckFileStatus getAckFileStatus();

        String getTraceFileName();
    }

    static interface Callback {

        boolean next(FileInfo file);
    }

    public void saveFile(FileType type, int version, String terminalID, Long receiptSeq, Date closeTime, byte[] header, byte[] body, Date createTime) throws Exception;

    public void saveFile(FileType type, int version, String terminalID, Long receiptSeq, Date closeTime, byte[] header, byte[] body, Date createTime, String recordID, AckFileStatus ackStatus) throws Exception;

    public int listFileInfo(Table table, Callback callback) throws Exception;

    public int listFileByState(Table table, State state, Callback callback) throws Exception;

    public int listNewFile(Table table, Callback callback) throws Exception;

    public void postponeSendFile(FileInfo file) throws Exception;

    public void banSendFile(FileInfo file) throws Exception;

    public void setFilesStates(Map<String, State> states) throws Exception;

    public void clean() throws Exception;

}

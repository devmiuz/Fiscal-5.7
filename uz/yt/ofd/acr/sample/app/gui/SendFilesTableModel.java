/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uz.yt.ofd.acr.sample.app.gui;

import uz.yt.ofd.acr.sample.app.storage.Storage;

/**
 *
 * @author administrator
 */
public class SendFilesTableModel extends FilesTableModel {

    static final String[] COLUMNS = new String[]{"RecordID", "Type", "Version", "TerminalID", "ReceiptSeq/CloseTime", "State", "CreateTime"};

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return COLUMNS[columnIndex];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Storage.FileInfo info = list.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return info.getRecordID();
            case 1:
                return info.getType().name();
            case 2:
                return info.getVersion();
            case 3:
                return info.getTerminalID();
            case 4:
                String rs = info.getReceiptSeq();
                String ct = info.getCloseTime();
                if (!rs.isEmpty()) {
                    return rs;
                }
                if (!ct.isEmpty()) {
                    return ct;
                }
                return "";
            case 5:
                return info.getState().name();
            case 6:
                return info.getCreateTime();
            default:
                return "";
        }
    }

}

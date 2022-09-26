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
public class RecvFilesTableModel extends FilesTableModel {

    static final String[] COLUMNS = new String[]{"RecordID", "Type", "TerminalID", "State", "AckFileStatus", "TraceFileName", "CreateTime"};

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
                return info.getTerminalID();
            case 3:
                return info.getState().name();
            case 4:
                return info.getAckFileStatus().name();
            case 5:
                return info.getTraceFileName();
            case 6:
                return info.getCreateTime();
            default:
                return "";
        }
    }

}

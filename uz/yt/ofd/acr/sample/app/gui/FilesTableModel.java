/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uz.yt.ofd.acr.sample.app.gui;

import java.util.LinkedList;
import java.util.List;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import uz.yt.ofd.acr.sample.app.storage.Storage;

/**
 *
 * @author administrator
 */
public abstract class FilesTableModel implements TableModel {

    protected List<Storage.FileInfo> list = new LinkedList();

    protected List<TableModelListener> liseners = new LinkedList();

    @Override
    public int getRowCount() {
        return list.size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        return;
    }

    @Override
    public void addTableModelListener(TableModelListener l) {
        liseners.add(l);
    }

    @Override
    public void removeTableModelListener(TableModelListener l) {
        liseners.remove(l);
    }

    public int size() {
        return list.size();
    }

    public boolean add(Storage.FileInfo e) {
        boolean b = list.add(e);
        for (TableModelListener l : liseners) {
            l.tableChanged(new TableModelEvent(this));
        }
        return b;
    }

    public void clear() {
        list.clear();
        for (TableModelListener l : liseners) {
            l.tableChanged(new TableModelEvent(this));
        }
    }

    public Storage.FileInfo get(int index) {
        return list.get(index);
    }
}

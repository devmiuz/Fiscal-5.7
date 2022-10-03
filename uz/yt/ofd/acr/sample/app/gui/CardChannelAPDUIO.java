/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uz.yt.ofd.acr.sample.app.gui;

import javax.smartcardio.CardChannel;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.swing.JTextArea;
import uz.yt.ofd.codec.applet.APDUCommand;
import uz.yt.ofd.codec.applet.APDUIO;
import uz.yt.ofd.codec.applet.APDUResponse;

/**
 *
 * @author administrator
 */
public class CardChannelAPDUIO implements APDUIO {

    final CardChannel cardChannel;
    final JTextArea log;

    public CardChannelAPDUIO(CardChannel cardChannel, JTextArea log) {
        this.cardChannel = cardChannel;
        this.log = log;
    }

    protected void appendAPDULog(String message) {
        log.append(message + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    @Override
    public APDUResponse transmit(APDUCommand command) throws Exception {
        appendAPDULog(command.toString());

        ResponseAPDU r = cardChannel.transmit(new CommandAPDU(command.getBytes()));
        APDUResponse response = new APDUResponse(r.getBytes());

        appendAPDULog(response.toString());
        return response;
    }

}

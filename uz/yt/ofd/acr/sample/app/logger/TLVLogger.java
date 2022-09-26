/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uz.yt.ofd.acr.sample.app.logger;

import java.util.Map;
import uz.yt.ofd.codec.DumpDescriptor;
import uz.yt.ofd.codec.tlv.TVS;

/**
 *
 * @author administrator
 */
public interface TLVLogger {

    public void appendDebugLogTLV(TVS tvs, String name, Map<String, String> tagDescriptionMap, String[] arrayOIDs);

    public void appendDebugLog(String message, Object... args);

    public void appendDebugLogAsText(Object o);

    public void appendDebugLogAsDumpDesciptor(DumpDescriptor dd);

    public void appendDebugLogKeyValue(String key, String value, int keyLetfPad);
}

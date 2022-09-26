/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uz.yt.ofd.acr.sample.app.sender;

import java.util.List;

/**
 *
 * @author administrator
 */
public interface Sender {

    void Sync(List<String> serverAddresses);
}

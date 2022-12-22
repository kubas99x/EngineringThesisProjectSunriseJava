package com.company;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDP2SendBack implements Runnable  {

    private DatagramSocket socket;
    public InetAddress address;
	public int port;
    public String messageToSend = "";
    public UDP2SendBack(DatagramSocket socket_,InetAddress address_, int port_, String messageToSend_){
    	socket = socket_;
    	address = address_;
    	port = port_;
    	messageToSend = messageToSend_;
    }
      public void run() {
        DatagramPacket packet = new DatagramPacket(messageToSend.getBytes(), messageToSend.getBytes().length, address, port);
        try {
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
        socket.close();
    }
}
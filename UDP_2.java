package com.company;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDP_2 implements Runnable  {

    private DatagramSocket socket;
    private boolean running;
    private byte[] buf = new byte[256];
    public String message = "";
    public InetAddress address;
	public int port;
    
    public UDP_2(DatagramSocket socket_){
    	socket = socket_;
    }

    public void run() {
        running = true;

        while (running) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
            address = packet.getAddress();
            port = packet.getPort();
            String received = new String(packet.getData(), 0, packet.getLength());
            received = received.replaceAll("\u0000.*", "");     
            message = received;
            String messageToSend = "message  recaived";
            DatagramPacket packet2 = new DatagramPacket(messageToSend.getBytes(), messageToSend.getBytes().length, address, port);
            try {
                socket.send(packet2);
            } catch (IOException e) {
                e.printStackTrace();
            }
            running = false;

        }
    }
}
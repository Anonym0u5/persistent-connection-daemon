package com.brunocapezzali;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;

/**
 * Rapresents the Daemon that accepts and manage all the connections. 
 * If a new connection is local then we have a {@link ConnectedScriptManager},
 * instead if the connection is remove we have a {@link ConnectedDeviceManager}.
 * Other than manage all the connection this class will maintain the list of all
 * connected and still active persistent sockets, so if a {@link Device} is
 * connected we can verifiy by asking to {@link MainServer}.
 * 
 * @author Bruno Capezzali
 * @see ConnectedDeviceManager
 * @see ConnectedScriptManager
 * @since 1.0.0
 */
public class MainServer extends Thread {
   private static final String TAG = "MainServer";

   private ServerSocket mServerSocket = null;
   private final long mStartTimestamp = System.currentTimeMillis();
   private InetAddress mLANAddress = null;
   private final HashMap<String, Device> mDevices = new HashMap<String, Device>();
   private boolean mStop = false;
   private boolean allRemoteConnection = false;
   
   public long getStartTimestamp() {
      return mStartTimestamp;
   }
   
   public void setAllRemoteConnection(boolean allRemoteConnection) {
      this.allRemoteConnection = allRemoteConnection;
   }
   
   synchronized public void stopServer() {
      Utils.log(TAG, "Server shutdowning ...");
      mStop = true;
      try {
         mServerSocket.close();
      } catch (IOException ioex ) {
         Utils.log(TAG, "Exception while stopping server: "+ ioex.getMessage());
      }
      removeAllDevices();
   }
   
   synchronized private void removeAllDevices() {
      for (String key : mDevices.keySet()) {
         removeDevice(mDevices.get(key));
      }
   }
   
   synchronized public void removeDevice(Device d) {
      Utils.log(TAG, "Removing device with uniqueIdentifier = "
              + d.getUniqueIdentifier()  +", connected since "
              + Utils.timestampDifferenceNow(d.getTimestampConnected()));
      
      mDevices.remove(d.getUniqueIdentifier());
      d.stopDevice();
      d.sockClose();
   }
         
   synchronized public Device getDevice(String uniqueIdentifier) {
      if ( mDevices.containsKey(uniqueIdentifier) ) {
         Device toReturn = mDevices.get(uniqueIdentifier);
         if ( toReturn.isDeviceActive() ) {
            return toReturn;
         } else {
            Utils.log(TAG, "Device with uniqueIdentifier = "
                    + uniqueIdentifier +" found, but NOT isDeviceActive()");
            removeDevice(toReturn);
         }
      }
      return null;
   }
   
   synchronized public void addDevice(Device device) {
      if ( mDevices.containsKey(device.getUniqueIdentifier()) ) {
         Device toRemove = mDevices.get(device.getUniqueIdentifier());
         Utils.log(TAG, "Device with uniqueIdentifier = "
                 + toRemove.getUniqueIdentifier() +" already in list.");
         removeDevice(toRemove);
      }
      mDevices.put(device.getUniqueIdentifier(), device);
      device.start();
   }
   
   synchronized public int getDevicesCount() {
      return mDevices.size();
   }
   
   private boolean isLocalConnection(InetAddress addr) {
      return ( addr.isLoopbackAddress() || addr.equals(mLANAddress) );
   }
   
   private void determinateLocalAddress() {
      try {
         mLANAddress = InetAddress.getLocalHost();
      } catch (UnknownHostException ex) {
         Utils.log(TAG, "Unable to determine local IP");
         System.exit(2);
      }      
   }

   @Override
   public void run() {
      determinateLocalAddress();
      
      try {
         mServerSocket = new ServerSocket(Config.kDaemonPort);
      } catch (IOException e) {
         Utils.log(TAG, "Could not listen on port: "+ Config.kDaemonPort);
         System.exit(2);
      }
      Utils.log(TAG, "Listening on "+ mLANAddress.toString() +":"
              + Config.kDaemonPort);
      
      Socket client;
      InetAddress clientAddress;
      while ( !mStop ) {
         try {
            client = mServerSocket.accept();
         } catch (IOException e) {
            Utils.log(TAG, "Accept failed: "+ e.getMessage());
            continue;
         }
         
         /* If we have a local connection then this is a script request else
          * we have a new connected device. */
         clientAddress = client.getInetAddress();         
         try {
            if ( isLocalConnection(clientAddress) && !allRemoteConnection ) {
               Utils.log(TAG, "New local script connection");
               ( new ConnectedScriptManager(this, client) ).start();
            } else {
               Utils.log(TAG, "New device connection ("+ clientAddress.toString() +")");
               ( new ConnectedDeviceManager(this, client) ).start();
            }
         } catch (IOException ioex) {
            Utils.log(TAG, "ERROR: Unable to manage the new connection: "
                    + ioex.getMessage());
         }
      }
   }
}
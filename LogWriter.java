//LogWriter.java

import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;

public class LogWriter {

   public Boolean loggingEnabled = false;
   private static String path = "";

   public LogWriter(String path){
      LogWriter.path = path;
   }

   public void write(String msg){
      try {
    	  if(loggingEnabled == false)return;
          File f = new File(path + System.getProperty("file.separator") + getLogFileName());
          BufferedWriter bw = new BufferedWriter(new FileWriter(f,true));
          bw.write(msg + "\n");
          bw.close();
      }
      catch(IOException e){
    	  System.out.println("Error: not able to create or open log file at" + path + System.getProperty("file.separator") + getLogFileName());
      }
   }

   public static String getLogFileName(){
      return new String("Luminator.log");
   }
}

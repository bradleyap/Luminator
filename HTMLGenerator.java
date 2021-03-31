import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;


public class HTMLGenerator {

	public Boolean writeHTML(String path, long anchorPos, long maxOutputLength){
		
		Boolean goodRslt = true;
		String tgtFilename = AppGlobals.htmlDir + path.replace("/","-") + ".html";
		String srcFilename = path;
		String outString = "";
		String logMsg = "";
    	File outFile = new File(tgtFilename); 
    	try{
    		FileWriter writer = new FileWriter(outFile);
    		outString = "<!DOCTYPE html><html><head><meta charset='UTF-8'/><title>" + path + "</title>";
    		outString += "<style>.color{background-color: #dfefff;}</style></head><body><h2>" + path + "</h2>";
    		writer.write(outString);
    		
    		Boolean foundTgt = false;
    		File file = new File(srcFilename);
    		long len = file.length();
    		long pos = 0;
    		if(len < anchorPos)anchorPos = len;
			BufferedReader f = new BufferedReader(new FileReader(file));
			String s = f.readLine();
			while(s != null){
				len = s.length();
				if(foundTgt==false && (anchorPos < (pos + len))){
					s = "<a name=\"clickTarget\"/>" + "<div class=\"color\">" + replaceAngleBrackets(s) + "</div>";//testing to see why clickTarget not being embedded
					//s += "<a name=\"clickTarget\"/>";
					//s = "<div class=\"color\">" + s;
					//s += "</div>";
					foundTgt = true;
				}
				else s = replaceAngleBrackets(s);
				s += "<br/>";
				writer.write(s);
				s = f.readLine();
				pos += len + 1;
			}
			
			writer.write(new String("</body></html>"));
			
    		writer.close();
			f.close();
    	}
		catch (FileNotFoundException fnfe){
			logMsg = fnfe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			AppGlobals.statusReportBuf += logMsg + "<br/>";
			goodRslt = false;
		}
    	catch(IOException ioe){
    		logMsg = ioe.getMessage();
			AppGlobals.logWriter.write(logMsg);
			AppGlobals.statusReportBuf += logMsg + "<br/>";
    		goodRslt = false;
    	}
    	return goodRslt;
	}

	
	public Boolean writeHTMLFromDerivedDocumentPosition(String path, long anchorPos, long maxOutputLength){
		Boolean goodRslt = true;
		String tgtFilename = AppGlobals.htmlDir + path.replace("/","-") + ".html";
		String srcFilename = path;
		String outString = "";
    	File outFile = new File(tgtFilename); 
    	try{
    		FileWriter writer = new FileWriter(outFile);
    		outString = "<!DOCTYPE html><html><head><meta charset='UTF-8'/><title>" + path + "</title>";
    		outString += "<style>.color{background-color: #efdfff;}</style></head><body><h2>" + path + "</h2>";
    		writer.write(outString);
    		
    		Boolean foundTgt = false;
    		File file = new File(srcFilename);
    		long len = file.length();
    		long pos = 0;
    		int expanded = 0;
			BufferedReader f = new BufferedReader(new FileReader(file));
			String line = f.readLine();
			String buf = "";
			while(line != null){
				len = line.length();
				expanded = computeNonOriginalCharCountAfterLineExpansion(line);
				if(foundTgt==false && (anchorPos < (pos + len))){
					buf = "<a name=\"clickTarget\"/>";
					//line = buf.substring(0,(int)(anchorPos - pos));
					buf += "<span class=\"color\">";
					buf += replaceAngleBrackets(line);//testing to see why clickTarget not being embedded
					//buf += "anchorPos = " + Long.toString(anchorPos);
					//buf += " | pos = " + Long.toString(pos);
					//line += buf.substring((int)(anchorPos - pos));
					buf += "</span>";
					foundTgt = true;
				}
				else buf = replaceAngleBrackets(line);
				if(len > 0)buf += "<br/>";
				else buf += "<p/>";
				writer.write(buf);
				line = f.readLine();
				pos += (len + expanded); // + 1; no original newlines are known when browser image is clicked
			}
			
			if(foundTgt==false){
				writer.write("<span class=\"color\"><a name=\"clickTarget\"/> ( ~ click target not found ~ ) </span>");
			}
			
			writer.write(new String("</body></html>"));
			
    		writer.close();
			f.close();
    	}
		catch (FileNotFoundException fnfe){
			System.out.println(fnfe.getMessage());
			goodRslt = false;
		}
    	catch(IOException ioe){
    		System.out.println(ioe.getMessage());
    		goodRslt = false;
    	}
    	return goodRslt;
	}
	
	public String replaceAngleBrackets(String inString){
		String s = "";
		int openIndex = -1;
		int closeIndex = -1;
		int pos = 0;
		openIndex = inString.indexOf("<");
		closeIndex = inString.indexOf(">");
		if(openIndex < 0 && closeIndex < 0)return inString;
		while(openIndex > -1 || closeIndex > -1){
			if(((openIndex < closeIndex) && (openIndex > -1)) || closeIndex < 0){
				s += inString.substring(pos,openIndex);
				s += "&lt;";
				pos = openIndex + 1;
			}
			else {
				s += inString.substring(pos,closeIndex);
				s += "&gt;";
				pos = closeIndex + 1;
			}
			openIndex = inString.indexOf("<",pos);
			closeIndex = inString.indexOf(">",pos);
		}
		if(pos < inString.length())s += inString.substring(pos);
		return s;
	}
	
	//compute how many non-original characters get inserted with this line
	public int computeNonOriginalCharCountAfterLineExpansion(String line){
		int nonOrigTotal = 0;
		int tadd = 0;
		final int len = line.length();
		int consumable = len;
		int totalInRaster = 0;
		int rasterIndex = 0;
		int chunk = 0;
		int chunkPlus = 0;
		int tabAdd = AppGlobals.tabSize - 1;
		int rasterLength = AppGlobals.rasterLength;
		if(tabAdd < 0)tabAdd = 0;
		if(len == 0)return rasterLength;
		int tIndex = line.indexOf("\t");
		int lastInRasterIndex = rasterLength - 1;//index into imaginary expanded version of line
		while(consumable > 0){
			while(tIndex > -1 && tIndex <= lastInRasterIndex){
				if((tIndex + nonOrigTotal + tabAdd) >= lastInRasterIndex)tadd = lastInRasterIndex - (tIndex + nonOrigTotal);
				else tadd = tabAdd;
				chunk = 1 + (tIndex - rasterIndex);
				chunkPlus = chunk + tadd;
				nonOrigTotal += tadd;
				totalInRaster += chunkPlus;
				rasterIndex += chunkPlus;
				consumable -= chunk;
				tIndex = line.indexOf("\t",tIndex + 1);
			}
			if((rasterIndex + consumable) <= (lastInRasterIndex + 1)){
				nonOrigTotal += (lastInRasterIndex + 1) - (rasterIndex + consumable);
				consumable = 0;
			}
			else{
				chunk = rasterLength - totalInRaster;
				consumable -= chunk;
				rasterIndex += chunk;
				totalInRaster = 0;
				lastInRasterIndex += rasterLength;				
			}
		}
		return nonOrigTotal;
	}
}






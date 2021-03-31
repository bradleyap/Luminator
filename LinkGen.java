//LinkGen.java - converts txt file of links to html
//bapliam@us.ibm.com
import java.io.*;

public class LinkGen {
	
	public static void main(String[] args) { 
		
		if(args.length < 2){
			System.out.println("An input file name, and output file name are required.");
			return;
		}
		
		String inFilename = args[0].trim();
		String outFilename = args[1].trim();
		if(inFilename.indexOf("html") > (inFilename.length() - 5)){
			System.out.println("input file cannot have an 'html' extension");
			return;
		}
		if((outFilename.indexOf("html") > (outFilename.length() - 5)) == false){
			System.out.println("output filename must have the .html extension.");
			return;
		}
		
    	File inFile = new File(inFilename); 
    	File outFile = new File(outFilename);
    	try{
    		Boolean atLink = false;
    		BufferedReader f = new BufferedReader(new FileReader(inFile));
    		FileWriter writer = new FileWriter(outFile,false);
			String line = f.readLine();
			String description = "";
			writer.write("<!DOCTYPE html><head></head><body>");
			while(line != null){
				if(line.indexOf("http") == 0){
					atLink = true;
				}
				if(line.indexOf("ftp") == 0){
					atLink = true;
				}
				if(atLink){
					writer.write(new String("<a href=\"") + line + new String("\">") + description + new String("</a><br/>"));
					description = "";
				}
				if(line.length() > 0)description = line;
				
				line = f.readLine();
				atLink = false;
			}
			writer.write("</body></html>");
    		writer.flush();
    		writer.close();
			f.close();
		}
		catch (FileNotFoundException fnfe){
			System.out.println(fnfe.getMessage());
		}
		catch (IOException ioe){
			System.out.println(ioe.getMessage());
		}	
    }
}


import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileNotFoundException;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;



public class ImageGenerator {
	
	public static String imgFilename = "";
	public static String fileNumberString = "";
	public static BufferedImage bi;
	static public HashMap<Long,String> bufferedKeychains = new HashMap<Long,String>();
	
	static void prepareBlockImage(int fileNumber, int blockNumber, ArrayList<Raster> rasterList){
    	//build temp file name
		fileNumberString = Integer.toString(fileNumber);
		imgFilename = "f" + fileNumberString + "_blk_" + Integer.toString(blockNumber) + ".png";
    	
    	//create Buffered image and write raster data to it
		try{
			int ht = AppGlobals.rastersPerColumn;
			int lsz = rasterList.size();
			int cols = (lsz / ht);// + 1;
			if((lsz % ht) != 0 )cols++;
			int imgWd = (cols * AppGlobals.rasterLength) + (cols * AppGlobals.gutterWd);
		    
	        bi = new BufferedImage(imgWd,ht,BufferedImage.TYPE_INT_RGB);
	        
	        //paint white background
	        Graphics2D g = bi.createGraphics();
	        g.setColor(new Color(0xff,0xff,0xff));//white
	        g.fillRect(0,0,imgWd,ht);
	        
			//for each raster write info in an encoded char stream
			int rasterListSize = rasterList.size();	
			int rasterLength = AppGlobals.rasterLength;
			for(int i=0; i<(rasterListSize);i++){
				rasterList.get(i).writeToBufferedImage(((i / ht) * (rasterLength + 5)),(i % ht),bi);
			}
		}
		catch(Exception e){
			System.out.println("Error: " + e.getMessage());
		}		
	}
	
	static void renderArtifactsToBuffer(ArrayList<Raster> rasterList, HashMap<Integer,Integer>nonOrigTotalsMap, HashMap<Integer,
										QueryObject> queryMap, ArrayList<String> keywordList, BagTracker bagTracker){

		if(rasterList==null || rasterList.get(0)==null){
			System.out.println("Raster list not valid in renderArtifactsToBuffer()");
			return;
		}
		
		int rlsz = rasterList.size();
		if(rasterList.get(rlsz - 1)==null){
			System.out.println("Raster list not valid in renderArtifactsToBuffer()");
			return;
		}
		
		Raster raster = rasterList.get(rlsz - 1);
		long bgnPos = rasterList.get(0).startFilePos;
		long endPos = raster.startFilePos + raster.getOrigCharCount() - 1;
		if(endPos < bgnPos){
			endPos = bgnPos;
		}
		
		HashMap<Integer,Integer> iterMap = new HashMap<Integer,Integer>();
		for(int i=0; i<keywordList.size(); i++){
			iterMap.put(i,0);
		}
		
		/*
		use the first filePosition to open the first related bagx file .. progress is primarily measured as we go through the 
		pertinent bagx files.. Once that is finished, the raster list will be converted to an image block
		for each keychain file that pertains to the raster position
		open the file, grab the keychains and see if they pertain .. short circuiting will happen when
		we reach a keychain for some keyword that is not in the bounds of the image block
			1) extract the filePos and keywordId from keychain
			2) iterate through the rasterlist, advancing the index held in iterMap for that particular keyword
			3) find the appropriate raster
			4) find the BufferedImage coordinates from the raster location info
			5) find the query using the queryMap
			6) using the queryObject effect information, alter the pixels in the Buffered Image
		 */

		String currentTag = ""; 
		String keywordInfo = "";
		String istr = "";
		String keychainLine = "";
		File f = null;
		raster = null;
		QueryObject query = null;
		Map.Entry<?, ?> pair = null;
		Iterator<?> buffMapIter = null;
		if(bufferedKeychains.size() > 0)buffMapIter = bufferedKeychains.entrySet().iterator();
		else {
			if(bagTracker.processed())currentTag = bagTracker.getNextTag();
			else currentTag = bagTracker.getCurrentTag();
			if(bagTracker.getCurrentMin() > endPos)return;
			bagTracker.processed(true);
		}
		int kIndex = -1;
		int dotIndex = -1;
		int dot2Index = -1;
		int rIndex = 0;
		int kwi = 0;
		long fpi = 0;
		int xpos = 0;
		int ypos = 0;
//		int wrapAmt = 0;
		int rasterLength = AppGlobals.rasterLength;
		int ht = AppGlobals.rastersPerColumn;
		int rasterListSz = rasterList.size();
		Boolean processingBuffer = false;
		Boolean buffering = false;
		Boolean guessRaster = true;
		Boolean rasterFound = false;
		Boolean retry = false;
		try{
			BufferedReader br = null; 
			if(buffMapIter!=null){
				if(buffMapIter.hasNext()){
					pair = (Map.Entry<?, ?>)buffMapIter.next();
					keychainLine = (String)pair.getValue();
				}
				processingBuffer = true;
			}
			else {
				if(currentTag.equals("$"))return; // || currentTag.equals("-1"))return;
				f = new File(AppGlobals.workingTmpDir + "f" + fileNumberString + "bag" + currentTag); //keychains held here
				br = new BufferedReader(new FileReader(f));
				keychainLine = br.readLine();
				while(keychainLine == null){
					br.close();
					currentTag = bagTracker.getNextTag();
					if(bagTracker.getCurrentMin() > endPos)return;
					bagTracker.processed(true);
					if(currentTag.equals("$"))return;
					f = new File(AppGlobals.workingTmpDir + "f" + fileNumberString + "bag" + currentTag);
					br = new BufferedReader(new FileReader(f));
					keychainLine = br.readLine();
				}
				processingBuffer = false;
			}
			while(keychainLine != null){ //keychains provided in order of least file position to greatest
				kIndex = keychainLine.indexOf("k");
				if(kIndex > -1){
					keywordInfo = keychainLine.substring(kIndex + 1);
					dotIndex = keywordInfo.indexOf(".");
					dot2Index = keywordInfo.indexOf(".",dotIndex + 1);
					if(dotIndex > -1){
						if(dot2Index > -1)fpi = Long.parseLong(keywordInfo.substring((dotIndex + 1),dot2Index)); //fpi - file position index
						else fpi = Long.parseLong(keywordInfo.substring(dotIndex + 1));
					//	if(processingBuffer)System.out.print("Buffer ");
					//	else System.out.print("Normal file stream ");
					//	System.out.println("keychain position: " + Long.toString(fpi));
					//	if(fpi == 401049){
					//		System.out.println("Here");
					//	}
						//if filepos is not in the range of the current block image, then we have finished
						if(fpi > endPos){
							if(processingBuffer){
								//this happened when an intervening block image claimed no keychain. Now with 
								//the bag Tracker min and max we should not arrive here
								System.out.println("ERROR: The bagTracker mechanism is not working.");
								return;
							}
							//we buffer because keychain bags will straddle the boundaries of our block image
							bufferedKeychains.put(fpi,keychainLine);
							buffering = true;
						}
						if(fpi < bgnPos){
							//these will either be negative file positions or stragglers who should not have been left behind
							System.out.println("ERROR: invalid keychain file position has been encountered:");
							System.out.println("the bgnPos of the image-block is: " + Long.toString(bgnPos));
							System.out.println("but the keychain is position " + Long.toString(fpi));
							break;
						}
						istr = keywordInfo.substring(0,dotIndex);
						kwi = Integer.parseInt(istr);
						if(processingBuffer==false)rIndex = iterMap.get(kwi);
						rasterFound = false;
						guessRaster = true;
						while(rasterFound==false && buffering==false){
							if(rIndex > (rasterListSz - 1))break;//some keychains are saved for later and should not be processed now
							raster = rasterList.get(rIndex);
							if((fpi - raster.startFilePos) <= (rasterLength + 100)){
								if(raster.holdsOriginalCharAt(fpi)){
									retry = false;
									rasterFound = true;
									xpos = ((rIndex / ht) * (rasterLength + 5)) + raster.getPixelOffsetFromFilePos(fpi);
									ypos = rIndex % ht;
									query = queryMap.get(kwi);
									if(query!=null){
										renderEffect(xpos,ypos,bi,query.effect,query.searchString.length());
//										if(query.searchString.length() > raster.surplusOriginal)
//											wrapAmt = query.searchString.length() - raster.surplusOriginal;
//										else wrapAmt = 0;
									}
									iterMap.put(kwi,rIndex); //used later to resume progress on the keyword at istr
								}
								else {
									if(fpi < raster.startFilePos){
										if(retry==false){
											retry = true;
											rIndex = 0;
										}
										else{
//											System.out.println("ERROR: Unexpected file position for keychain. Not finding raster position!");
//											System.out.println("\tkeychain: " + Long.toString(fpi) + ", raster: " + Long.toString(raster.startFilePos));											
										}
									}
								}
							}
							else{
								if(guessRaster && retry==false){
									int rasterIndex = ((int)(fpi - bgnPos)) / (rasterLength + 1);
									int extraWhite = 0;
									if(nonOrigTotalsMap.containsKey(rasterIndex)){
										extraWhite = nonOrigTotalsMap.get(rasterIndex);
									}
									int guessIndex = rasterIndex + (extraWhite/(2 * rasterLength));//some risk of overshooting raster
									if(guessIndex > (rIndex + 2))rIndex = guessIndex - 2;
									guessRaster = false;
								}
							}
							rIndex++;
						}//end while(rasterFound == false)
					}
				}
				if(buffMapIter!=null){
					if(buffMapIter.hasNext()){
						pair = (Map.Entry<?, ?>)buffMapIter.next();
						keychainLine = (String)pair.getValue();
						processingBuffer = true;
						rIndex = 0;
					}
					else {
						buffMapIter = null;
						bufferedKeychains.clear();
						keychainLine = "";
					}
				}
				if(buffMapIter==null){				
					//infrequent processing
					if(processingBuffer){
						processingBuffer = false;
						currentTag = bagTracker.getNextTag();
						if(bagTracker.getCurrentMin() > endPos)return;
						bagTracker.processed(true);
						if(currentTag.indexOf("$") > -1){ //end of tag sequence reached
							keychainLine = null;
							break;
						}
						f = new File(AppGlobals.workingTmpDir + "f" + fileNumberString + "bag" + currentTag); //keychains held here
						br = new BufferedReader(new FileReader(f));
	//					System.out.println("New file keychain file: bag" + currentTag);
					
					}
					else{
						keychainLine = br.readLine();	
						//if starting to read br
						if(keychainLine == null){
							if(buffering){
								br.close();
								break;//currentBag straddles image-block boundary
							}
							currentTag = bagTracker.getNextTag();
							if(bagTracker.getCurrentMin() > endPos)return;
							bagTracker.processed(true);
							if(currentTag.indexOf("$") > -1){ //end of tag sequence reached
								keychainLine = null;
								break;
							}
							br.close();
							f = new File(AppGlobals.workingTmpDir + "f" + fileNumberString + "bag" + currentTag); //keychains held here
							br = new BufferedReader(new FileReader(f));
		//					System.out.println("New file keychain file: bag" + currentTag);
							keychainLine = br.readLine();
						}			
					}
				}
			}//end while(keychainLine != null)
		}
		catch(FileNotFoundException fnfe){
			System.out.println("renderArtifactsToBuffer() error: " + fnfe.getMessage());
			System.out.println("file not found probably: f" + fileNumberString + "bag" + currentTag);
		}
		catch(IOException ioe){
			System.out.println("renderArtifactsToBuffer() error: " + ioe.getMessage());
		}
	}
	
//	static int calculatePixelArrayOffset(int rasterIndex,Raster raster,long filePos){
//		int index = -1;
//		int ht = AppGlobals.rastersPerColumn;
//		if(imgWd < 1)return -1;
//		
//		raster.getPixelOffsetFromFilePos(pos)
//		return index;
//	}
	
	static void renderEffect(int x, int y, BufferedImage bi, Effect effect, int keywordLen){
		if(effect.type == EffectType.COLOR){
			int xpos = x;
			int imgLen = bi.getWidth();
			//if(xpos + keywordLen >= bi.getWidth())keywordLen = (bi.getWidth() - (xpos + 1));
			for(int i=0; i<keywordLen; i++){
				if(xpos == imgLen)break;		
				bi.setRGB(xpos++,y,effect.getColor());
			}
		}
	}
	
	static void outputBlockImage(){
    	
    	//create Buffered image and write raster data to it
		try{
	            
		    //write to file
	        File outputfile = new File(AppGlobals.imageDir + imgFilename);
	        ImageIO.write(bi, "png", outputfile);
			
		}
		catch(IOException ioe){
			System.out.println("Error: " + ioe.getMessage());
		}
	}
	
	static void createDotPlotImgBuf(int w, int h, int hrzPad){
		bi = new BufferedImage(w + (2 * hrzPad),h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(new Color(0xff,0xff,0xff));//white
        g.fillRect(0,0,w + (2 * hrzPad),h);
        g.setColor(new Color(0xcc,0xcf,0x00));//pea green
        g.fillRect(hrzPad,(h / 2) -1,w,3);
	}
	
	static void renderPlot(int x, int y, int r, int g, int b){
		Graphics2D gfx = bi.createGraphics();
		gfx.setColor(new Color(r,g,b));
		gfx.fillOval(x - 4,y,8,8);
	}
	
	static void renderPlotRangeSqBracket(int x,int width,int startHt,int baseline,int r, int g, int b){
		Graphics2D gfx = bi.createGraphics();
		gfx.setColor(new Color(r,g,b));
		gfx.drawLine(x,startHt,x,baseline);
		gfx.drawLine(x,startHt,x + width,startHt);
		gfx.drawLine(x + width,startHt,x + width,baseline);
		//gfx.drawRect(x,startHt,width,baseline - startHt);
	}
	
	static void renderPlotRange(int x,int wd,int y,int ht,int r, int g,int b){
		Graphics2D gfx = bi.createGraphics();
		gfx.setColor(new Color(r,g,b));
		gfx.drawRect(x,y,wd,ht);
	}

	
	static void renderPlotRangeRounded(int x,int width,int startHt,int baseline,int r, int g, int b){
		Graphics2D gfx = bi.createGraphics();
		gfx.setColor(new Color(0,255,0));
		gfx.drawLine(x + 10,startHt,x + width - 10,startHt);
		gfx.drawLine(x + 10,20,x + width - 10,20);
		gfx.drawArc(x,0,20,20,90,180);
		gfx.drawArc(x + width - 20,0,20,20,270,180);
	}
	
	static void outputDotPlotImage(int index){
        
    	//create Buffered image and write raster data to it
		try{
		    //write to file
	        File outputfile = new File(AppGlobals.imageDir + "dotPlot" + Integer.toString(index) + new String(".png"));
	        ImageIO.write(bi,"png", outputfile);
			
		}
		catch(IOException ioe){
			System.out.println("Error: " + ioe.getMessage());
		}
	}
	
	//method is not finished
	public Boolean outputImagesFromCharsegData(int blockCount){
		int sz = AppGlobals.blockSize * (AppGlobals.rasterLength + AppGlobals.gutterWd);
		char [] cbuf = new char[sz];
		String filePrefix = "f" + fileNumberString;
		for(int i=1; !(i>blockCount); i++){
					
			String blk = filePrefix + "_blk_" + Integer.toString(i);
			String img = filePrefix + "_img_" + Integer.toString(i) + ".png";
			String buf = "";
			String subStr = "";
			File inFile = new File("f"+ fileNumberString + blk);
			try{
				int pos = 0;
				int spcIndex = -1;
				int dollarIndex = -1;
				int dotIndex = -1;
				BufferedReader f = new BufferedReader(new FileReader(inFile));
				int charsRead = f.read(cbuf,0,sz);
				buf = new String(cbuf);
				spcIndex = buf.indexOf(" ");
				dollarIndex = buf.indexOf("$");
				dotIndex = buf.indexOf(".");
								
				while(pos < charsRead){
					if(spcIndex > -1){
						break;
					}
					
					subStr = buf.substring(pos,spcIndex);
					//parse $
					if(subStr.charAt(pos) != '$'){
						System.out.println("Error reading raster, expected '$'");
						break;
					}
					pos++;
					
					//parse filePos
					
					
					//parse character segments
					if(dollarIndex > -1 || dotIndex > -1){
						
					}
					dollarIndex = buf.indexOf("$");
					dotIndex = buf.indexOf(".");
				}
				
				int imgWd = 48;
				int ht = 48;
				int numPxls = imgWd * ht;
			    int[] pxls = new int [numPxls];
			    int red = 160;
			    int grn = 100;
			    int blu = 200;
			    int j = 0;
			    for(j=0; j<numPxls; j++){
			    	if(j < 1000)pxls[j] = (red<<16) | (grn<<8) | blu;
			    	else pxls[j] = 0x00<<16 | 0x55<<8 | 0xff;
			    }


		        BufferedImage bi = new BufferedImage(imgWd,ht,BufferedImage.TYPE_INT_RGB);
			    for(j=0; j<numPxls; j++){
			    	if(j < 1000)pxls[j] = (red<<16) | (grn<<8) | blu;
			    	else pxls[j] = 0x00<<16 | 0x55<<8 | 0xff;
			    	bi.setRGB(j / 48,j % 48,pxls[j]);
			    }
		            
		        File outputfile = new File("/opt/logviewer/image/" + img);
		        ImageIO.write(bi, "png", outputfile);
				
		        f.close();
			}
			catch(FileNotFoundException fnfe){
				System.out.println("Error: " + fnfe.getMessage());
			}
			catch(IOException e) {
		        System.out.println("oops..cant save image. Not sure why ImageGenerate.outputImagesFromCharsegData is failing.");
		    }
		
 
		}
		
		return false;
	} 
	
}

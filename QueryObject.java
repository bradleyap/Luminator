import java.util.ArrayList;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class QueryObject {
	
	public String id = "";
	public String searchString = "";
	public String action = "";
	public int searchStringId = -1;
	public Effect effect = null; //graphical effect to apply to each instance of found object
	public static long currentStacktracePos = 0;
	public static long currentLinePos = 0;
	private QueryObject parent = null;
	private QueryObject child = null; //for complex queries
	private QueryObject next = null;
	private String queryObjectTypeStr = "k"; //default is 'k' {k=keyword, l=line, t=stacktrace, g=group}
	//private QueryObjectRelationship relationship = QueryObjectRelationship.CONTAINS;
	//private String stmt = "";
	public String beginTime = "";
	public String endTime = "";;
	public HashMap<String,ArrayList<KeywordInstance>> instances = new HashMap<String,ArrayList<KeywordInstance>>();
	private ArrayList<KeywordInstance> instanceFragments = new ArrayList<KeywordInstance>();
	
	//not in use, using TextTranslator.collectAspectData
	public void findInstances(String filename,BufferedReader reader, int consumed){
		if(searchString.length() > 0){
			String line = "";
			String fragStr = "";
			String subStr = "";
			ArrayList<KeywordInstance> kwdList = null;
			long filePos = 0;
			int index;
			int lineLen = 0;
			Boolean haveNewlineForFragmentedInstance = false;
			instanceFragments.clear();
			try{
				while((line = reader.readLine()) != null){
					lineLen = line.length();
					//pick up remaining fragments of keyword instances
					for(KeywordInstance frag : instanceFragments){
						//BLOCKING HERE PREVENTED CONCURRENT MODIFICATION EXCEPTION
						if(frag == null || haveNewlineForFragmentedInstance == false)break;
						//for now, not handling case where part of search term straddles more than two lines
						fragStr = frag.kwd.substring(frag.numMatched);
						subStr = line.substring(0,fragStr.length());
						if(fragStr.equals(subStr) != false){
							//KeywordInstance kwi = new KeywordInstance(searchString,filePos + index);
							frag.numMatched = frag.kwd.length();
							if(instances.containsKey(filename))kwdList = instances.get(filename);
							else {
								kwdList = new ArrayList<KeywordInstance>();
								instances.put(filename,kwdList);
							}
							kwdList.add(frag);							
						}
						instanceFragments.remove(frag);
					}
					//look for new keyword instances
					index = line.indexOf(searchString);
					while(index > -1){
						KeywordInstance kwi = new KeywordInstance(searchString,filePos + (long)index,searchString.length());
						if(instances.containsKey(filename))kwdList = instances.get(filename);
						else {
							kwdList = new ArrayList<KeywordInstance>();
							instances.put(filename,kwdList);
						}
						kwdList.add(kwi);
						index = line.indexOf(searchString,index + 1);
					}
					//pick up instances split over lines
					String s = searchString.substring(0,1);
					int fragLen = searchString.length() - 1; //subtract 1 so not redundantly picking up keyword at very end of line
					index = line.indexOf(s,lineLen - fragLen);
					haveNewlineForFragmentedInstance = false; //not implemented yet
					while(index > -1 && haveNewlineForFragmentedInstance){
						//check to be sure search string has newline character at end of fragment
						fragLen = lineLen - index;
						fragStr = searchString.substring(0,fragLen);
						subStr = line.substring(index,index + fragLen);
						if(fragStr.equals(subStr)){
							instanceFragments.add(new KeywordInstance(searchString,filePos + index,fragLen));
						}
						index = line.indexOf(s,(lineLen - fragLen) + 1);
					}
					filePos += line.length() + 1; //additional character is omitted newline
				}
			}
			catch(IOException e){
				
			}
		}
		if(child!=null)child.findInstances(filename,reader,consumed);
	}
	
	public void assembleSearchStringListAndQueryObjectMap(ArrayList<String> keywordList, HashMap<Integer,QueryObject> queryObjectMap){
		if(searchString.length() > 0){
			searchStringId = keywordList.size();
			keywordList.add(searchString);
			queryObjectMap.put(searchStringId,this);
		}
		if(child!=null)child.assembleSearchStringListAndQueryObjectMap(keywordList,queryObjectMap);
		if(next!=null)next.assembleSearchStringListAndQueryObjectMap(keywordList,queryObjectMap);
	}
	
	public void assembleQueryRenderingData(String json, int id){
		json += "{\"searchStringId\":" + Integer.toString(id) + ",\"color\":\"" + effect.clr + "\"}";
		if(child!=null)child.assembleQueryRenderingData(json,id++);
		if(next!=null)next.assembleQueryRenderingData(json,id++);
	}
	
	public String getColorData(){
		if(effect.type == EffectType.COLOR)return "\"" + effect.clr + "\"";
		return "\"#000000\"";
	}
	
	public String getKeychain(int keywordId, Long filePos, String time){
		String keychain = "";
		if(keywordId == searchStringId){
			keychain = queryObjectTypeStr + Integer.toString(searchStringId) + new String(".") + Long.toString(filePos) + 
					new String(".") + time;
		}
		if(parent!=null){
			keychain = parent.buildKeychain() + new String(">") + keychain;
		}
		return keychain;
	}
	
	public String buildKeychain(){
		String keychain = queryObjectTypeStr;
		long pos = -1;
		if(queryObjectTypeStr.equals("l"))pos = currentLinePos;
		if(queryObjectTypeStr.equals("t"))pos = currentStacktracePos;
		keychain += Long.toString(pos);
		if(parent!=null){
			keychain = parent.buildKeychain() + new String(">") + keychain;
		}
		return keychain;
	}
	
	public void dumpObject(){
		System.out.print("{id:");
		System.out.print(id);
		System.out.print(",searchString:");
		System.out.print(searchString);
		if(effect!=null){
			System.out.print(",");
			effect.dumpEffect();
		}
		System.out.print(",beginTime:");
		System.out.print(beginTime);
		System.out.print(",endTime:");
		System.out.print(endTime);
		System.out.print(" hits: ");
	    Iterator<?> it = instances.entrySet().iterator();
	    while (it.hasNext()) {
	        Map.Entry<?, ?> pairs = (Map.Entry<?, ?>)it.next();
	        System.out.print("{");
	        System.out.print(pairs.getKey() + " = ");
	        if(pairs.getValue() != null){
	        	@SuppressWarnings("unchecked")
				ArrayList<KeywordInstance> kwdList = (ArrayList<KeywordInstance>)pairs.getValue();
	        	for(KeywordInstance kwd : kwdList){
	        		if(kwd == null)break;
	        		kwd.dumpMe();
	        	}
	        }
	        System.out.print("}");
	    }
		System.out.println("}");
	}
}

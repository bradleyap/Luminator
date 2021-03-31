import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.JOptionPane;

public class ObjectDescriptionParser {
	
	public ArrayList<QueryObject> list = null;
	public ArrayList<String> keywords = null;
	QueryObject currentQuery = null;
	private String stmt = "";
	private int nextKwdPos = -1;
	private int start = 0;
	private String nextKeyword = "";
	
	public ArrayList<QueryObject> parse(String str){
		list = new ArrayList<QueryObject>();
		int consumed = 0;
		int strLen = str.length();
		int nlIndex = -1;
		int index = 0;
		String stmt = "";
		String qstr = str + new String("\n");
		while(consumed < strLen){
			nlIndex = qstr.indexOf("\n",index);
			if(nlIndex > -1){
				stmt = qstr.substring(index,nlIndex);
				currentQuery = new QueryObject();
				if(!eatStatment(stmt)){
					System.out.println("error while parsing statment: " + stmt);
				}
				list.add(currentQuery);
				index = nlIndex + 1;
				consumed = consumed + stmt.length() + 1;
			}
			else break;
		}
		
		//dumpQueryObjects();
		return list;
	}
	
	Boolean eatStatment(String statment){
		int len = statment.length();
		Boolean rslt = false;
		start = 0;
		stmt = statment;
		findNextKwd();
		//if first word is not a keyword it is an identifier or name for an object
		if(nextKwdPos > 0){
			rslt = consumeId();
			if(!rslt){
				reportUnrecognizableInput(new String("trying to find object identifier"));
				return false;
			}
			while(start < len){
				rslt = consumeObjectDescription();
				if(!rslt){
					reportGrammaticalError(new String("trying to parse statement:") + statment);
					return false;
				}
				if(nextKeyword.equals(",") != true)break;
			}
		}
		else { //otherwise we are doing an action rather than defining something. Actions are more
				//complex because they contain decriptive elements
				rslt = consumeActionStatment();
		}

		return rslt;
	}
	
	
	private Boolean consumeId(){
		//whitespace is not required so the required id is everything that precedes 
		//the first keyword
		findNextKwd();
		if(nextKwdPos < 0)return false;
		currentQuery.id = stmt.substring(start,nextKwdPos);
		currentQuery.id = currentQuery.id.trim();
		start = nextKwdPos;//consume or eat advances start, unlike nextKeyword method
		return true;
	}
	
	private Boolean consumeObjectDescription(){
		
		//use nextKeyword and eat it
		if(nextKeyword.equals(","))advanceTo(nextKwdPos + 1,true);
		if(nextKeyword.equals("is")){
			//followed by EFFECT
			advanceTo(nextKwdPos + nextKeyword.length(),true);
			if(nextKeyword.equals("color") || nextKeyword.equals("symbol")){
				String attribute = nextKeyword;
				int i = stmt.indexOf(new String(":"),start);
				if(i < 0){
					reportGrammaticalError(new String("trying to find ':' between attribute and value"));
					return false;
				}
				start = i + 1;
				int lastPos = stmt.indexOf(",",start);
				if(lastPos < 0)lastPos = stmt.length();
				String value = readTo(lastPos);
				value.trim();
				currentQuery.effect = new Effect(attribute,value);
				findNextKwd();
			}			
		}
		else if(nextKeyword.equals("contains")){
			//do not put top level searchstring here, 'contains' is for children of this object
		}
		//reset start
		//reset nextKeyword
		return true;
	}
	
	private Boolean consumeActionStatment(){
		Boolean err = false;
		Boolean atActionKeyword = nextKeyword.equals("show") ||
				nextKeyword.equals("plot") || nextKeyword.equals("frame");
		if(atActionKeyword){
			currentQuery.action = nextKeyword;
			//what follows must be string literal, object modifier, or identifier (not necessarily known)
			advanceTo(nextKwdPos + nextKeyword.length(),true);
			//check for object modifiers
			consumeWhitespace();
			if(atStringLiteral()){ //isStringLiteral(nextWord)){
				//currentQuery.searchString = nextWord.substring(1,nextWord.length() - 1);
				consumeStringLiteral();
				findNextKwd();
				if(nextKeyword.equals("in")){
					advanceTo(nextKwdPos + 2,false);
					consumeWhitespace();
					consumeColor();
				}
				if(nextKeyword.equals("from")){
					advanceTo(nextKwdPos + 4,true);
					consumeWhitespace();
					consumeDateTime(true);
					findNextKwd();
					if(nextKeyword.equals("to")){
						advanceTo(nextKwdPos + 2,true);
						consumeWhitespace();		
						consumeDateTime(false);
						findNextKwd();
						if(nextKeyword.equals("in")){
							advanceTo(nextKwdPos + 2,false);
							consumeWhitespace();
							consumeColor();
						}			
					}

				}
			}
			else{
				int i = stmt.indexOf(" ",start);
				int ti = stmt.indexOf("\t",start);
				if((ti > -1) && (ti < i))i = ti;
				if(i < 0)i = stmt.length();
				String nextWord = readTo(i);
				if(isObjectModifier(nextWord)){
					err = consumeSpecialId();
					if(err){
					
					}
				}
				else {  //treat the next word as an identifier, 
					//is identifier known?
					
					//otherwise create object with list
					
				}
			}	
		}
		return true;
	}
	
	private Boolean consumeStringLiteral(){
		String quot = "'";
		if(stmt.indexOf("\"",start) == 0)quot = "\"";
		int indexEnd = stmt.indexOf(quot,start + 1);
		currentQuery.searchString = stmt.substring(start + 1,indexEnd);
		start = indexEnd + 1;
		return false;
	}

	//a special id is identifier preceded by modifier, usually indicates granularity
	private Boolean consumeSpecialId(){
		return false;
	}
	
	private Boolean consumeColor(){
		int i = stmt.indexOf(" ",start);
		if(i < 0)i = stmt.length();
		String value = readTo(i);
		String attribute = new String("color");
		currentQuery.effect = new Effect(attribute,value);
		return true;
	}
	
	private Boolean consumeDateTime(Boolean beginTimeframe){
		String dtStr = "";
		dtStr = read(AppGlobals.testTimeFmt.length());
		//dtStr = read(AppGlobals.timeFmt.length());
//		SimpleDateFormat fmt = //new SimpleDateFormat(AppGlobals.timeFmt);
//				new SimpleDateFormat(AppGlobals.testTimeFmt);
//		try{
//			//Date parsedDate = fmt.parse(dtStr);			
//		}
//		catch(ParseException e){
//			System.out.print("Wrong date format in query string.");
//		}
		if(beginTimeframe)currentQuery.beginTime = dtStr;
		else currentQuery.endTime = dtStr;
		return true;
	}
/*	
	private Boolean isStringLiteral(String str){
		if((str.indexOf("'") == 0) && (str.indexOf("'",1) == str.length() - 1))return true;
		if((str.indexOf("\"") == 0) && (str.indexOf("\"",1) == (str.length() - 1)))return true;
		return false;
	}
	*/
	private Boolean atStringLiteral(){
		if(stmt.indexOf("'",start) == start)return true;
		if(stmt.indexOf("\"",start) == start)return true;
		return false;
	}
	
	private Boolean isObjectModifier(String str){
		if(str.equals("line"))return true;
		if(str.equals("block"))return true;
		if(str.equals("region"))return true;
		if(str.equals("group"))return true;
		return false;
	}
	
	// findNextKwd() simply sets nextKeyword string and the index nextKwdPos after
	// the current start position, it does not move start
	private int findNextKwd(){
		int nxtIndex = -1;
		nextKwdPos = -1;
		nextKeyword = "";
		for(String kwd : keywords){
			nxtIndex = stmt.indexOf(kwd,start);
			if(nxtIndex > -1){
				if(nextKwdPos == -1 || nxtIndex < nextKwdPos){
					nextKeyword = kwd;
					nextKwdPos = nxtIndex;
				}
			}
		}
		return nextKwdPos;
	}
	
	// advanceBy(n) skips n characters, advances start
	private void advanceBy(int numChars, Boolean updateKwdPos){
		if((start + numChars) > nextKwdPos){
			if(updateKwdPos)findNextKwd();
		}
		start += numChars;
	}

	// advanceTo(i) skips to index, advances start, update if flag is true
	private void advanceTo(int pos,Boolean updateKwdPos){
		start = pos;
		if(updateKwdPos){
			findNextKwd();
		}
	}
	
	//	read(n) returns the next n numbers after 'start', then moves start to the start + n position	
	private String read(int n){
		String s = stmt.substring(start,start + n);
		start += n;
		return s;
	}
	
	// readTo(i) returns all characters from the current start position through i - 1, 
	// then sets start to i
	private String readTo(int index){
		String s = "";
		try{
			s = stmt.substring(start,index);
			start = index;
		}
		catch(IndexOutOfBoundsException e){
			System.out.println("Error: " + e.getMessage());
		}
		return s;
	}
	
	//eatWhitespace() advances position past the current whitespace
	private void consumeWhitespace(){
		int ti = 0;
		int i = 0;
		Boolean bgn = true;
		while(ti > -1 || i > -1){
			if(bgn)bgn = false;
			else {
				if(ti==start || i==start)start++;
				else break;
			}
			ti = stmt.indexOf("\t",start);
			i = stmt.indexOf(" ",start);
		}
	}
	
	private Boolean consume(String str){
		int printableIndex = stmt.indexOf("");
		stmt = stmt.trim();
		if(stmt.indexOf(str)==0){
			start = printableIndex + 1;
			return true;
		}
		return false;
	}
	
	private void reportUnrecognizableInput(String failedAction){
		String problem = new String("There was a problem during action:");
		String recommendation = new String(". Be sure that identifiers are included for each descriptive statment");
		System.out.print(problem);
		System.out.print(failedAction);
		System.out.print(recommendation);
		JOptionPane.showMessageDialog(null, problem + failedAction + recommendation);
	}
	
	private void reportGrammaticalError(String failedAction){
		String problem = new String("There was a problem during action:");
		String recommendation = new String(". Check that descriptive statments are grammatically correct.");
		System.out.print(problem);
		System.out.print(failedAction);
		System.out.print(recommendation);
		JOptionPane.showMessageDialog(null, problem + failedAction + recommendation);	
	}

	
	public void initializeKeywordList(){
		keywords = new ArrayList<String>();
		keywords.add(new String("is"));
		keywords.add(new String("show"));
		keywords.add(new String("plot"));
		keywords.add(new String("frame"));
		keywords.add(new String("contains"));
		keywords.add(new String("para")); //in many cases a paragraph is simply a line
		keywords.add(new String("region")); //block or text area inhabited by some group of object instances
		keywords.add(new String("color"));
		keywords.add(new String("symbol"));
		keywords.add(new String("in"));
		keywords.add(new String("from"));
		keywords.add(new String("with"));
		keywords.add(new String("to"));
		keywords.add(new String(":"));
		keywords.add(new String(","));
	}
	
	public ArrayList<String> getKeywordList(){
		return keywords;
	}
	
	private void dumpQueryObjects(){
		for(QueryObject ob : list){
			if(ob == null)break;
			ob.dumpObject();
		}
	}
}

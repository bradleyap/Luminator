
public class Effect {
	EffectType type = EffectType.COLOR;
	public int red = 0;
	public int grn = 0;
	public int blu = 0;
	public String clr = "#000000";
	public String symbol = "none";
	
	public Effect(String attribute, String value){
		if(attribute.equals("color")){
			type = EffectType.COLOR;
			extractColor(value);
		}
		if(attribute.equals("symbol")){
			type = EffectType.SYMBOL;
			symbol = value;
		}
	}
	
	private Boolean extractColor(String color){
		if(color.indexOf("#") > -1){
			redFromHexString(color.substring(1,3));
			greenFromHexString(color.substring(3,5));
			blueFromHexString(color.substring(5,7));
			clr = color;
		}
		else {
			if(color.equals("blue")){
				blu = 255;
				clr = "#0000ff";
			}
			if(color.equals("black")){
				red = 0;
				grn = 0;
				blu = 0;
				clr = "#000000";
			}
			if(color.equals("red")){
				red = 255;
				clr = "#ff0000";
			}
			if(color.equals("green")){
				grn = 255;
				clr = "#00ff00";
			}
			if(color.equals("yellow")){
				red = 255;
				grn = 255;
				clr = "#ffff00";
			}
			if(color.equals("purple")){
				red = 155;
				grn  = 0;
				blu = 160;
			}
			if(color.equals("orange")){
				red = 255;
				grn = 140;
				blu = 0;
			}
		}
		return true;
	}
	
	private void redFromHexString(String redStr){
		red = Integer.parseInt(redStr,16);
	}
	
	private void blueFromHexString(String blueStr){
		blu = Integer.parseInt(blueStr,16);
	}
	
	private void greenFromHexString(String greenStr){
		grn = Integer.parseInt(greenStr,16);
	}
	
	public int getColor(){
		return red<<16 | grn<<8 | blu;
	}
	
	public void dumpEffect(){
		System.out.print("effect:{type:");
		if(type == EffectType.COLOR){
			System.out.print("COLOR");
			System.out.print(",red:" + (new Integer(red)).toString());
			System.out.print(",grn:" + (new Integer(grn)).toString());
			System.out.print(",blu:" + (new Integer(blu)).toString());
		}
		if(type == EffectType.SYMBOL)System.out.print("SYMBOL");
		System.out.println("}");

	}
}

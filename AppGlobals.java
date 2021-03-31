import java.util.ArrayList;

public class AppGlobals 
{
	static double phi = 1.61803;
	static int dotPlotPositions = 500;
	static long largeFileThreshold = 999999;
	static int linesPerBatch = 5000;
	static int maxCharactersPerHashDomain = 50000;
	static int pxlsPerVrtLine = 3;
	static String lastKeychain = "";
	static int blockSize = 2400;
	static int gutterWd = 5;
	static int rastersPerColumn = 200;
	static int rasterLength = 50;
	static int tabSize = 8;
	static int white = 0xff<<16 | 0xff<<8 | 0xff;
	static int lightGrey = 0xdd<<16 | 0xdd<<8 | 0xdd;
	static int dirtyWhite = 0xf6<<16 | 0xf6<<8 | 0xf6;
	static Boolean avoidWordSplitting = false;
	static ArrayList<String> filenames = null;
	static ObjectDescriptionParser parser = null;
	static LogWriter logWriter = new LogWriter("/opt/logwatcher");//putting logs in the current directory
	static final String timeFmt = "MM-dd-yyyy hh:mm:ss:SSS";
	static final String testTimeFmt = "MM-dd";
	static String imageDir = "/opt/logwatcher/images/";
	static String htmlDir = "/opt/logwatcher/html/";
	static String logDir = "/opt/logwatcher/logs/";
	static String workingDir = "/opt/logwatcher/";
	static String workingTmpDir = "/opt/logwatcher/tmp/";
	static String appName = "Luminator";
	static String statusReportBuf = "";
	static Boolean debug = false;
	static int activeCodeRgns = 0; //tells us if certain lines of test code are in use, subject to human error if 
									//code is not fully uncommented
	static Boolean bufferIncorrect = false; //for streamHandling test
	static long shortCircuitMax = 1000; //for diagnosing when problems happen in a large file stream
	
}

package mllab_clinss;
import java.io.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class ClinssProcess {
	public static void processDir(String srcdir, String dstdir) throws Exception {
		for(String file : (new File(srcdir)).list()) {
			// Extract relevant text from xml
			Document xdoc = Jsoup.parse(new File(srcdir, file), "UTF-8");
			String title = xdoc.select("title").text();
			String date = xdoc.select("date").text();
			String content = xdoc.select("content").text();
			// Write text to file in dest directory
			PrintWriter dstfile = new PrintWriter(new File(dstdir, file));
// 			dstfile.println(title+"\n"+contents);
			dstfile.printf("<story>\n");
			dstfile.printf("<title>%s</title>\n", title);
			dstfile.printf("<date>%s</date>\n", date);
			dstfile.printf("<content>\n");
			dstfile.printf("%s\n", content);
			dstfile.printf("</content>\n");
			dstfile.printf("</story>\n");
			dstfile.close();
		}
	}
	public static void main(String[] args) throws Exception {
		String usage = "java mllab_clinss.ClinssProcess COMMAND [ARGS]";
		if (args.length == 0) {
			System.out.println("Usage: " + usage);
			System.exit(1);
		}
		String cmd = args[0];
		int i = 1;
		if ("procdir".equals(cmd)) {
			String srcdir = args[i++];
			String dstdir = args[i++];
			processDir(srcdir, dstdir);
		}
	}
}

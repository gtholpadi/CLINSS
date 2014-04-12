package mllab_clinss;

import java.io.File;
import java.io.FileInputStream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class TestDemo {
	private TestDemo() {}
	/** Parse command-line params and call the requested method. */
	public static void main (String[] args) throws Exception {
		String usage = "java mllab_clinss.TestDemo"
			+ " COMMAND [ARGS]";
		if (args.length==0) {
			System.out.println("Usage: " + usage);
			System.exit(1);
		}
		String cmd = args[0];
		int i = 1;
		if ("indexfiles".equals(cmd)) {
			String indexPath = args[i++];
			String docsPath = args[i++];
			boolean update = args[i++].equals("update") ? true : false;
			String stopPath = args[i++];
			String inferencer = args[i++];
			String instancefile = args[i++];
			String indexType = args[i++];
			Clinss.setInstance(new Clinss(inferencer, instancefile, indexType));
			indexFiles(indexPath, docsPath, update, stopPath);
		} else if ("testing".equals(cmd)) {
			testing();
		}
	}
	/** Test code. */
	public static void testing() throws Exception{
		/*quickly test something*/
// 			SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy");
// 			Date d = df.parse("28-8-2010");
// 			System.out.println(d + " " + d.getTime() +  " " + d.getTime()/86400000l);
	}
	/** Index all text files under a directory. */
	public static void indexFiles(String indexPath, String docsPath, boolean update, String stopPath) throws Exception {
		Directory dir = FSDirectory.open(new File(indexPath));
		Analyzer analyzer;
		analyzer = Clinss.getInstance().getAnalyzer(stopPath);
		IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_44, analyzer);
		if (update) {
			iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
		} else {
			iwc.setOpenMode(OpenMode.CREATE);
		}
		IndexWriter writer = new IndexWriter(dir, iwc);
		indexDocs(writer, new File(docsPath));
		writer.close();
	}
	/**
	 * Indexes the given file using the given writer, or if a directory is given,
	 * recurses over files and directories found under the given directory. */
	static void indexDocs(IndexWriter writer, File file) throws Exception {
		if (!file.canRead()) {
			return;
		}
		if (file.isDirectory()) {
			String[] files = file.list();
			if (files != null) {
				for (int i = 0; i < files.length; i++) {
// 					System.out.println("indexDocs: "+files[i]);
					indexDocs(writer, new File(file, files[i]));
				}
			}
		} else {
			FileInputStream fis;
			fis = new FileInputStream(file);
			Document doc = getDoc(file, fis);

			if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
				writer.addDocument(doc);
			} else {
				writer.updateDocument(new Term(Clinss.FLDTYPE_PATH, file.getPath()), doc);
			}
			fis.close();
		}
	}
	static Document getDoc(File file, FileInputStream fis) throws Exception {
		Document doc = new Document();
		makeDocClinss(file, fis, doc);
		return doc;
	}
	static void makeDocClinss(File file, FileInputStream fis, Document doc) throws Exception {
		Clinss.getInstance().makeDoc(file, fis, doc);
	}
}

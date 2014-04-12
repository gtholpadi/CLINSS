package mllab_clinss;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.Version;
import org.jsoup.Jsoup;

import cc.mallet.pipe.Pipe;
import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.InstanceList;

public class Clinss {
	static Clinss inst = null;
	static String IDXTYPE_TOPIC = "TOPIC";
	static String FLDTYPE_PATH = "path";
	static String FLDTYPE_TITLE = "title";
	static String FLDTYPE_PUBDATE = "pubdate";
	static String FLDTYPE_CONTENTS = "contents";
	TopicInferencer ti = null;
	Pipe instancePipe = null;
	String indexType = null;
	SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy");
	long invPrecision = 86400000l;//number of milliseconds in a day
	public Clinss() throws Exception {
	}
	public Clinss(String inferencer, String instancefile, String indexType) throws Exception {
		this.indexType = indexType;
		if (indexType.equals(Clinss.IDXTYPE_TOPIC)) {
			ti = TopicInferencer.read(new File(inferencer));
			instancePipe = InstanceList.load(new File(instancefile)).getPipe();
			System.out.println(instancePipe);
		}
	}
	public static void setInstance(Clinss inst1) {
		inst = inst1;
	}
	public static Clinss getInstance() throws Exception {
		if (inst == null) {
			inst = new Clinss();
		}
		return inst;
	}
	public Analyzer getAnalyzer(String stopPath) throws Exception {
		Analyzer analyzer = null;
		if ((new File(stopPath)).isFile()) {
			analyzer = new StandardAnalyzer(Version.LUCENE_44,
				new BufferedReader(new InputStreamReader(new FileInputStream(stopPath))));
		} else {
			analyzer = new StandardAnalyzer(Version.LUCENE_44);
		}
		return analyzer;
	}
	public void makeDoc(File file, FileInputStream fis, Document doc) throws Exception {
		doc.add(new StringField(Clinss.FLDTYPE_PATH, file.getPath(), Field.Store.YES));
		if (indexType.equals(Clinss.IDXTYPE_TOPIC)) {
			makeTopicDoc(file, fis, doc);
		} else {
			makeWordDoc(file, fis, doc);
		}
	}
	public Long parseDate(String date) throws Exception {
		return df.parse(date).getTime()/invPrecision;
	}
	public void makeWordDoc(File file, FileInputStream fis, Document doc) throws Exception {
		ClinssDoc cdoc = ClinssDoc.parse(file);
		String title = cdoc.title;
		String date = cdoc.date;
		String contents = cdoc.content;
		doc.add(new StringField(Clinss.FLDTYPE_TITLE, title, Field.Store.YES));
/*		date = DateTools.dateToString(df.parse(date), DateTools.Resolution.DAY);
		doc.add(new StringField(FLDTYPE_PUBDATE, date, Field.Store.YES));*/
		long ldate = parseDate(date);
		System.out.println(file.getName() + ": " + ldate);
		doc.add(new LongField(Clinss.FLDTYPE_PUBDATE, ldate, Field.Store.YES));
		doc.add(new TextField(Clinss.FLDTYPE_CONTENTS, contents, Field.Store.NO));
	}
	public void makeTopicDoc(File file, FileInputStream fis, Document doc) throws Exception {
		// read text of doc
		ClinssDoc cdoc = ClinssDoc.parse(file);
		String title = cdoc.title;
		String date = cdoc.date;
		String contents = cdoc.content;
		doc.add(new StringField(Clinss.FLDTYPE_TITLE, title, Field.Store.YES));
		doc.add(new StringField(Clinss.FLDTYPE_PUBDATE, date, Field.Store.YES));
		doc.add(new TextField(Clinss.FLDTYPE_CONTENTS, contents, Field.Store.NO));

		//////// infer topics for text
		InstanceList instances = new InstanceList(instancePipe);
		String[] strarr = { title+"\n"+contents};
		instances.addThruPipe(new StringArrayIterator(strarr));
		double[] dist = ti.getSampledDistribution(instances.get(0), 100, 10, 10);
		// take topics upto 0.8 prob mass, or greater than .05
		DoubleArrayIndexComparator comparator = new DoubleArrayIndexComparator(dist);
		Integer[] indexes = comparator.createIndexArray();
		Arrays.sort(indexes, comparator);
		double tot = 0;
		for (int j=0; j<indexes.length; j++) {
			int i = indexes[j];
			if (tot > 0.8 || dist[i] < 0.05) break;
			tot += dist[i];
			Field f = new TextField(Integer.toString(i), Integer.toString(i), Field.Store.NO);
			f.setBoost((float)dist[i]);
			doc.add(f);
		}
	}
	public Query getQuery(String line) throws Exception {
		if (indexType.equals(Clinss.IDXTYPE_TOPIC)) {
			return getTopicQuery(line);
		} else {
			return getExactQuery(line);
			//return getFuzzyQuery(line);
		}
	}
	public Query getExactQuery(String line) throws Exception {
		String [] terms = line.trim().split("\\s+");
		BooleanQuery bq = new BooleanQuery();
		for (String term : terms) {
			//TermQuery qpat = new TermQuery(new Term("path", term));
			TermQuery qtit = new TermQuery(new Term(Clinss.FLDTYPE_TITLE, term));
			TermQuery qcon = new TermQuery(new Term(Clinss.FLDTYPE_CONTENTS, term));

			//bq.add(qpat, BooleanClause.Occur.SHOULD);
			bq.add(qtit, BooleanClause.Occur.SHOULD);
			bq.add(qcon, BooleanClause.Occur.SHOULD);
		}
		return bq;
	}
	public Query getFuzzyQuery(String line) throws Exception {
		String [] terms = line.trim().split("\\s+");
		BooleanQuery bq = new BooleanQuery();
		for (String term : terms) {
			//TermQuery qpat = new TermQuery(new Term("path", term));
			FuzzyQuery qtit = new FuzzyQuery(new Term(Clinss.FLDTYPE_TITLE, term), 1);
			FuzzyQuery qcon = new FuzzyQuery(new Term(Clinss.FLDTYPE_CONTENTS, term), 1);

			//bq.add(qpat, BooleanClause.Occur.SHOULD);
			bq.add(qtit, BooleanClause.Occur.SHOULD);
			bq.add(qcon, BooleanClause.Occur.SHOULD);
		}
		return bq;
	}
	public Query getTopicQuery(String line) throws Exception {
		BooleanQuery bq = new BooleanQuery();
		// Get word query
		bq = (BooleanQuery) getExactQuery(line);

		// Get topic query
		InstanceList instances = new InstanceList(instancePipe);
		String[] strarr = {line};
		instances.addThruPipe(new StringArrayIterator(strarr));
		double[] dist = ti.getSampledDistribution(instances.get(0), 100, 10, 10);
		//take topics upto 0.8 prob mass
		DoubleArrayIndexComparator comparator = new DoubleArrayIndexComparator(dist);
		Integer[] indexes = comparator.createIndexArray();
		Arrays.sort(indexes, comparator);
		double tot = 0;
		for (int j=0; j<indexes.length; j++) {
			int i = indexes[j];
			if (tot > 0.8 || dist[i] < 0.05) break;
			tot += dist[i];
			Query q = new TermQuery(new Term(Integer.toString(i), Integer.toString(i)));
			q.setBoost((float)dist[i]);
			bq.add(q, BooleanClause.Occur.SHOULD);
		}
		return bq;
	}
	public static void main(String[] args) throws Exception {
		ClinssDoc doc = ClinssDoc.parse(new File(args[0]));
		System.out.println(doc.title + doc.date + "QWE\n" + doc.content + "qwe");
	}
}

class ClinssDoc {
	static String UTF8_BOM = "\uFEFF";
	String title = "";
	String date = "";
	String content  = "";
	private ClinssDoc(){
	}
	public static ClinssDoc parse(File file) throws Exception {
		ClinssDoc doc = new ClinssDoc();
		org.jsoup.nodes.Document xdoc = Jsoup.parse(file, "UTF-8");
		doc.title = xdoc.select("title").text();
		doc.date = xdoc.select("date").text();
		doc.content = xdoc.select("content").text();
		return doc;
	}
	public static ClinssDoc parse_old(File file) throws Exception {
		ClinssDoc doc = new ClinssDoc();
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
		String line;
//		int s, e;
		boolean instory = false;
		boolean incont = false;
		while ((line = br.readLine()) != null) {
			if (line.startsWith(UTF8_BOM)) {
				line = line.substring(1);
			}
			if (line.startsWith("<story>")) {
				instory = true;
				continue;
			} else if (line.startsWith("<content>")) {
				incont = true;
				continue;
			} else if (line.startsWith("</content>")) {
				incont = false;
				continue;
			} else if (line.startsWith("</story>")) {
				instory = false;
				continue;
			}
			if (instory) {
				if (incont) {
					doc.content += line + "\n";
				} else if (line.startsWith("<title>")) {
					doc.title = line.substring("<title>".length(), line.indexOf("</title>"));
				} else if (line.startsWith("<date>")) {
					doc.date = line.substring("<date>".length(), line.indexOf("</date>"));
				}
			}
		}
		br.close();
		return doc;
	}
}

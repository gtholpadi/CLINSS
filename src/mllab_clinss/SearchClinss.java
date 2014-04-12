package mllab_clinss;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class SearchClinss {
	private IndexReader reader = null;
	public IndexSearcher searcher = null;
	private Analyzer analyzer;
	private QueryParser titleParser;
	private QueryParser contentParser;
	public int maxHits = 100;
	public Float ttqueryBoost = 0.0f;
	public Float tcqueryBoost = 3.0f;
	public Long dateWindow = 30l;
//	private String indexType;
//	private TopicInferencer ti;
//	private Pipe instancePipe;
	private Clinss clinss;

	public SearchClinss(String indexPath, String inferencer, String instancefile,
		String indexType, String stopPath, Float ttqueryBoost, Float tcqueryBoost,
		Long dateWindow) throws Exception {
//		this.indexType = indexType;
		clinss = new Clinss(inferencer, instancefile, indexType);
		reader = DirectoryReader.open(FSDirectory.open(new File(indexPath)));
		searcher = new IndexSearcher(reader);
		analyzer = clinss.getAnalyzer(stopPath);
		titleParser = new QueryParser(Version.LUCENE_44, Clinss.FLDTYPE_TITLE, analyzer);
		contentParser = new QueryParser(Version.LUCENE_44, Clinss.FLDTYPE_CONTENTS,
			analyzer);
		if (ttqueryBoost != null) this.ttqueryBoost = ttqueryBoost;
		if (tcqueryBoost != null) this.tcqueryBoost = tcqueryBoost;
		if (dateWindow != null) this.dateWindow = dateWindow;
		//for query generation
		if (indexType.equals(Clinss.IDXTYPE_TOPIC)) {
//			ti = TopicInferencer.read(new File(inferencer));
//			instancePipe = InstanceList.load(new File(instancefile)).getPipe();
		}
	}
	public Query getQuery(String line) throws Exception {
		return clinss.getQuery(line);
	}
	public TopDocs search(Query query) throws Exception {
		return searcher.search(query, maxHits);
	}
	public Document doc(int doc) throws Exception {
		return searcher.doc(doc);
	}

	public Query getDocQuery(File qfile) throws Exception {
		ClinssDoc qdoc = ClinssDoc.parse(qfile);
		//title in title
		Query ttquery = titleParser.parse(QueryParser.escape(qdoc.title));
// 		Query ttquery = titleParser.parse(QueryParser.escape(qdoc.title + " " + qdoc.content.split("।")[2]));
		ttquery.setBoost(ttqueryBoost);
		//title in content
		Query tcquery = contentParser.parse(QueryParser.escape(qdoc.title));
// 		Query tcquery = contentParser.parse(QueryParser.escape(qdoc.title + " " + qdoc.content.split("।")[2]));
		tcquery.setBoost(tcqueryBoost);
		//content in content
		Query ccquery = contentParser.parse(QueryParser.escape(qdoc.content));
		//date range
		long ldate = clinss.parseDate(qdoc.date);
		NumericRangeQuery<Long> dquery = NumericRangeQuery.newLongRange(Clinss.FLDTYPE_PUBDATE,
			ldate-dateWindow, ldate+dateWindow, true, true);
		BooleanQuery query = new BooleanQuery();
		query.add(ttquery, BooleanClause.Occur.SHOULD);
		query.add(tcquery, BooleanClause.Occur.SHOULD);
		query.add(ccquery, BooleanClause.Occur.SHOULD);
		query.add(dquery, BooleanClause.Occur.MUST);
// 		query.add(dquery, BooleanClause.Occur.SHOULD);
		return query;
	}
	public TopDocs searchDoc(File qfile) throws Exception {
// 		ClinssDoc qdoc = ClinssDoc.parse(qfile);
// 		//title in title
// 		Query ttquery = titleParser.parse(QueryParser.escape(qdoc.title));
// 		ttquery.setBoost(ttqueryBoost);
// 		//title in content
// 		Query tcquery = contentParser.parse(QueryParser.escape(qdoc.title));
// 		tcquery.setBoost(tcqueryBoost);
// 		//content in content
// 		Query ccquery = contentParser.parse(QueryParser.escape(qdoc.content));
// 		//date range
// 		long ldate = clinss.parseDate(qdoc.date);
// 		NumericRangeQuery dquery = NumericRangeQuery.newLongRange(Clinss.FLDTYPE_PUBDATE,
// 			ldate-dateWindow, ldate+dateWindow, true, true);
// 		BooleanQuery query = new BooleanQuery();
// 		query.add(ttquery, BooleanClause.Occur.SHOULD);
// 		query.add(tcquery, BooleanClause.Occur.SHOULD);
// 		query.add(ccquery, BooleanClause.Occur.SHOULD);
// 		query.add(dquery, BooleanClause.Occur.MUST);
		Query query = getDocQuery(qfile);
		return searcher.search(query, maxHits);
	}
	public static void searchfiles(String indexPath, String indexType, String inferencer,
		String instancefile, String stopPath, String queryDir, String runsFile,
		Float ttqueryBoost, Float tcqueryBoost, Long dateWindow)
		throws Exception {
		SearchClinss sc = new SearchClinss(indexPath, inferencer, instancefile, indexType,
			stopPath, ttqueryBoost, tcqueryBoost, dateWindow);
		BufferedWriter bw = new BufferedWriter(new FileWriter(runsFile));
		boolean done = false;
		for (File f : (new File(queryDir)).listFiles()) {
			String qpath = f.getName();
			System.out.println("Searching: "+qpath);
			TopDocs docs = sc.searchDoc(f);
			int i = 0;
			for (ScoreDoc hit : docs.scoreDocs) {
				i++;
				String dpath = sc.doc(hit.doc).get(Clinss.FLDTYPE_PATH);
				//english-document-00001.txt Q0 hindi-document-00345.txt 1 0.4644
				String line = String.format("%s Q0 %s %d %f\n", qpath,
					(new File(dpath)).getName(), i, hit.score);
				bw.write(line);
				if (!done) {
					done = true;
					System.err.println(qpath + " " + dpath);
					System.err.println(sc.searcher.explain(sc.getDocQuery(f), hit.doc).toHtml());
					BooleanQuery bq = (BooleanQuery) sc.getDocQuery(f);
					for (BooleanClause bc : bq.clauses()) {
						Query q = bc.getQuery();
						System.err.println(q.getBoost() + "------" + q);
					}
				}
			}
		}
		bw.close();
	}
	public static void main(String[] args) throws Exception {
		String usage = "java mllab_clinss.SearchClinss"
			+ " COMMAND [ARGS]";
		if (args.length==0) {
			System.out.println("Usage: " + usage);
			System.exit(1);
		}
		String cmd = args[0];
		int i = 1;
		if ("searchfiles".equals(cmd)) {
			String indexPath = args[i++];
			String indexType = args[i++];
			String inferencer = args[i++];
			String instancefile = args[i++];
			String stopPath = args[i++];
			String queryDir = args[i++];
			String runsFile = args[i++];
			searchfiles(indexPath, indexType, inferencer, instancefile,
				stopPath, queryDir, runsFile, null, null, null);
		} else if (cmd.equals("test")) {
			String cont = "   प्रमुख  संवाददाता ।।  मुंबई  13 फरवरी के जर्मन  बेकरी ब्लास्ट पर  एटीएस के दो बड़े  अधिकारियों के  परस्पर विरोधी  बयानों ने इस केस  को अब उलझा दिया  है। एटीएस के पुणे";
			String[] rec = cont.split("।");
			for (String s : rec) {
				System.out.println("QWE"+s+"ASD");
			}
		}
	}
}

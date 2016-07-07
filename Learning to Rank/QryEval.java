/*
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.
 */

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * QryEval is a simple application that reads queries from a file,
 * evaluates them against an index, and writes the results to an
 * output file.  This class contains the main method, a method for
 * reading parameter and query files, initialization methods, a simple
 * query parser, a simple query processor, and methods for reporting
 * results.
 * <p>
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * Everything could be done more efficiently and elegantly.
 * <p>
 * The {@link Qry} hierarchy implements query evaluation using a
 * 'document at a time' (DaaT) methodology.  Initially it contains an
 * #OR operator for the unranked Boolean retrieval model and a #SYN
 * (synonym) operator for any retrieval model.  It is easily extended
 * to support additional query operators and retrieval models.  See
 * the {@link Qry} class for details.
 * <p>
 * The {@link RetrievalModel} hierarchy stores parameters and
 * information required by different retrieval models.  Retrieval
 * models that need these parameters (e.g., BM25 and Indri) use them
 * very frequently, so the RetrievalModel class emphasizes fast access.
 * <p>
 * The {@link Idx} hierarchy provides access to information in the
 * Lucene index.  It is intended to be simpler than accessing the
 * Lucene index directly.
 * <p>
 * As the search engine becomes more complex, it becomes useful to
 * have a standard approach to representing documents and scores.
 * The {@link ScoreList} class provides this capability.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  public static HashMap<Integer, Double> pageRank = new HashMap<>();
  public static HashSet<Integer> featuresDisable;

  private static final EnglishAnalyzerConfigurable ANALYZER =
    new EnglishAnalyzerConfigurable(Version.LUCENE_43);
  private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink", "keywords" };
  public static Map<String, String> parameters;
  public static String trainingQueryFile;
  public static String relevanceJudgmentFile;
  public static String trainingFeatureVectorsFile;
  public static String pageRankFile;
  public static String featureDisable;
  public static String svmRankLearnPath;
  public static String svmRankClassifyPath;
  public static String svmRankParamC;
  public static String svmRankModelFile;
  public static String testingFeatureVectorsFile;
  public static String testingDocumentScores;
  public static String output;
  public static String c;
  public static double mu;
  public static double lambda;
  public static double BM25k1;
  public static double BM25b;
  public static double BM25k3;
  public static String queryFilePath;


  public static ArrayList<ArrayList<QDFeatures>> QDs = new ArrayList<ArrayList<QDFeatures>>();





  public static boolean isExpansion = false;

  //  --------------- Methods ---------------------------------------

  /**
   * @param args The only argument is the parameter file name.
   * @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {

    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.
    
    Timer timer = new Timer();
    timer.start ();

    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException ("ERROR: Insufficient arguments!");
    }



    //  Configure query lexical processing to match index lexical
    //  processing.  Initialize the index and retrieval model.

    ANALYZER.setLowercase(true);
    ANALYZER.setStopwordRemoval(true);
    ANALYZER.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);

    parameters = readParameterFile (args[0]);

    Idx.initialize(parameters.get("indexPath"));

    //  parameters
    trainingQueryFile = parameters.get("letor:trainingQueryFile");
    relevanceJudgmentFile = parameters.get("letor:trainingQrelsFile");
    trainingFeatureVectorsFile = parameters.get("letor:trainingFeatureVectorsFile");
    pageRankFile = parameters.get("letor:pageRankFile");
    queryFilePath = parameters.get("queryFilePath");
    svmRankLearnPath = parameters.get("letor:svmRankLearnPath");
    svmRankClassifyPath = parameters.get("letor:svmRankClassifyPath");
    svmRankParamC = parameters.get("letor:svmRankParamC");
    svmRankModelFile = parameters.get("letor:svmRankModelFile");
    testingFeatureVectorsFile = parameters.get("letor:testingFeatureVectorsFile");
    testingDocumentScores = parameters.get("letor:testingDocumentScores");
    output = parameters.get("trecEvalOutputPath");
    mu = Double.parseDouble(parameters.get("Indri:mu"));
    lambda = Double.parseDouble(parameters.get("Indri:lambda"));
    BM25k1 = Double.parseDouble(parameters.get("BM25:k_1"));
    BM25b = Double.parseDouble(parameters.get("BM25:b"));
    BM25k3 = Double.parseDouble(parameters.get("BM25:k_3"));

    // disable features
    featuresDisable = new HashSet<>();
    if (parameters.containsKey("letor:featureDisable")) {
      featureDisable = parameters.get("letor:featureDisable");
      disableFeatures(featureDisable);
    }

    // training
    readPageRank(pageRankFile);
    ArrayList<ArrayList<String>> trainingQueries = generateQueries(trainingQueryFile);// [0] is qid [1:] are terms
    for (ArrayList<String> queryWords : trainingQueries){//per query
      ArrayList<QDFeatures> featuresList = readRelevanceJudgement(relevanceJudgmentFile, queryWords.get(0), queryWords.subList(1,queryWords.size()));
      normalizeFeatures (featuresList);
      QDs.add(featuresList);
    }

    writeSVMFile(trainingFeatureVectorsFile);
    train(svmRankLearnPath, svmRankParamC, trainingFeatureVectorsFile, svmRankModelFile);


    trainingQueries.clear();
    QDs.clear();

    // testing
    ArrayList<ArrayList<String>> testingQueries = generateQueries(queryFilePath);
    testing(testingQueries);
    writeSVMFile(testingFeatureVectorsFile);
    classify(svmRankClassifyPath, testingFeatureVectorsFile, svmRankModelFile, testingDocumentScores);
    printResults();

    timer.stop();
    System.out.println ("Time:  " + timer);
  }


  public static void testing(ArrayList<ArrayList<String>> queries) throws IOException {

    for (ArrayList<String> queryWords : queries){// per query
      int qid = Integer.parseInt(queryWords.get(0));
      List<String> terms = queryWords.subList(1, queryWords.size());
      ArrayList<QDFeatures> featuresList = new ArrayList<>();

      List<DocScore> docScoreList = new ArrayList<DocScore>();

      //compute QTF
      HashMap<String, Integer> querySet = new HashMap<>(5);
      for (String word : terms){
        if (querySet.containsKey(word)){
          querySet.put(word, querySet.get(word)+1);
        }else{
          querySet.put(word,1);
        }
      }





      // compute BM25 for all docs that contain at least onr terms
      HashSet<Integer> set = new HashSet<>();

      for (String term : terms){// per term
        InvList invList = new InvList(term, "body");
        int df = invList.df;
        for (int i=0; i < df; i++){
          set.add(invList.getDocid(i));
        }
      }



      for (int docid : set){
        TermVector termVector = new TermVector(docid, "body");
        if (termVector.positionsLength() > 0 && termVector.stemsLength() > 0){
          int i;
          double score = 0;
          String stem = null;
          for (i=1; i < termVector.stemsLength(); i++){
            stem = termVector.stemString(i);
            if (querySet.containsKey(stem)){
              score += getScoreBM25(i, termVector, "body") * (BM25k3+1)*querySet.get(stem)/(BM25k3+querySet.get(stem));
            }
          }
          docScoreList.add(new DocScore(docid, score));
        }

      }



      Collections.sort(docScoreList);
      docScoreList = docScoreList.subList(0, Math.min(100, docScoreList.size()));

      //generate features
      for (DocScore docScore : docScoreList) {
        QDFeatures qd = new QDFeatures(qid, terms, docScore.docid, BM25k1, BM25b, BM25k3, mu, lambda, "0");
        featuresList.add(qd);
      }
      normalizeFeatures(featuresList);
      QDs.add(featuresList);
    }



  }
  public static double getScoreBM25 (int stemId, TermVector termVector, String field) throws IOException {

    double tf = termVector.stemFreq(stemId);

    double docLength = termVector.positionsLength();
    double N = Idx.getNumDocs();//get doc count
    double avgDocLength = ((double)Idx.getSumOfFieldLengths(field))/(double)Idx.getDocCount(field);
    double df = termVector.stemDf(stemId);
    double rsj = 0;
    if (df < N/2){
      rsj = Math.log((N - df + 0.5) / (df + 0.5));
    }
    double tfweight = tf/(tf+BM25k1*(1-BM25b+BM25b*docLength/avgDocLength));
    return rsj*tfweight;
  }

  public static void train(String execPath, String c, String qrelsFeatureOutputFile, String modelOutputFile) throws Exception {
    // runs svm_rank_learn from within Java to train the model
    // execPath is the location of the svm_rank_learn utility,
    // which is specified by letor:svmRankLearnPath in the parameter file.
    // FEAT_GEN.c is the value of the letor:c parameter.
    Process cmdProc = Runtime.getRuntime().exec(
            new String[] { execPath, "-c", c, qrelsFeatureOutputFile,
                    modelOutputFile });

    // The stdout/stderr consuming code MUST be included.
    // It prevents the OS from running out of output buffer space and stalling.

    // consume stdout and print it out for debugging purposes
    BufferedReader stdoutReader = new BufferedReader(
            new InputStreamReader(cmdProc.getInputStream()));
    String line;
    while ((line = stdoutReader.readLine()) != null) {
      System.out.println(line);
    }
    // consume stderr and print it for debugging purposes
    BufferedReader stderrReader = new BufferedReader(
            new InputStreamReader(cmdProc.getErrorStream()));
    while ((line = stderrReader.readLine()) != null) {
      System.out.println(line);
    }

    // get the return value from the executable. 0 means success, non-zero
    // indicates a problem
    int retValue = cmdProc.waitFor();
    if (retValue != 0) {
      throw new Exception("SVM Rank crashed.");
    }
  }


  public static void classify(String execPath, String testingFeatureVectorsFile, String modelOutputFile, String output) throws Exception {
    // runs svm_rank_learn from within Java to train the model
    // execPath is the location of the svm_rank_learn utility,
    // which is specified by letor:svmRankLearnPath in the parameter file.
    // FEAT_GEN.c is the value of the letor:c parameter.
    Process cmdProc = Runtime.getRuntime().exec(
            new String[] { execPath, testingFeatureVectorsFile,
                    modelOutputFile, output });

    // The stdout/stderr consuming code MUST be included.
    // It prevents the OS from running out of output buffer space and stalling.

    // consume stdout and print it out for debugging purposes
    BufferedReader stdoutReader = new BufferedReader(
            new InputStreamReader(cmdProc.getInputStream()));
    String line;
    while ((line = stdoutReader.readLine()) != null) {
      System.out.println(line);
    }
    // consume stderr and print it for debugging purposes
    BufferedReader stderrReader = new BufferedReader(
            new InputStreamReader(cmdProc.getErrorStream()));
    while ((line = stderrReader.readLine()) != null) {
      System.out.println(line);
    }

    // get the return value from the executable. 0 means success, non-zero
    // indicates a problem
    int retValue = cmdProc.waitFor();
    if (retValue != 0) {
      throw new Exception("SVM Rank crashed.");
    }
  }


  public static void writeSVMFile(String filename) throws IOException {
    PrintWriter writer = new PrintWriter(filename);
    for (ArrayList<QDFeatures> query : QDs){//per query
      for (QDFeatures qd : query){//per document
        String line = qd.relevance + " qid:" + qd.qid + " ";
        for (int i=0; i < 18; i++){
          line += Integer.toString(i + 1) + ":" + qd.features[i] + " ";
        }
        line += "# " + Idx.getExternalDocid(qd.docid);
        writer.println(line);
      }
    }

    writer.close();
  }

  public static void normalizeFeatures (ArrayList<QDFeatures> featuresList){
    double[] minVal = new double [18];
    double[] maxVal = new double [18];
    int i;
    for (i=0; i < 18; i++){
      minVal[i] = Double.MAX_VALUE;
      maxVal[i] = 0;
    }

    for (QDFeatures features : featuresList){//per doc
      //Features
      for (i=0; i < 18; i++) {
        if (!Double.isNaN(features.features[i]) && features.features[i] < minVal[i]) {//f1
          minVal[i] = features.features[i];
        } else if (!Double.isNaN(features.features[i]) && features.features[i] > maxVal[i]) {
          maxVal[i] = features.features[i];
        }
      }

    }

    for (QDFeatures features : featuresList){//per doc
      for (i=0; i < 18; i++) {
        if ((maxVal[i] == minVal[i]) || Double.isNaN(features.features[i])){
          features.features[i] = 0;
        } else {
          features.features[i] = (features.features[i] - minVal[i]) / (maxVal[i] - minVal[i]);
        }
      }

      //debug

      /*
      System.out.println("qid:" + features.qid + " Docid:" + features.docid);
      System.out.println("spamScore:" + features.features[0]);
      System.out.println("urlDepth:" + features.features[1]);
      System.out.println("fromWiki:" + features.features[2]);
      System.out.println("pageRank:" + features.features[3]);
      System.out.println("bm25Body:" + features.features[4]);
      System.out.println("indriBody:" + features.features[5]);
      System.out.println("overlapBody:" + features.features[6]);
      System.out.println("bm25Title:" + features.features[7]);
      System.out.println("indriTitle:" + features.features[8]);
      System.out.println("overlapTitle:" + features.features[9]);
      System.out.println("bm25Url:" + features.features[10]);
      System.out.println("indriUrl:" + features.features[11]);
      System.out.println("overlapUrl:" + features.features[12]);
      System.out.println("bm25Inlink:" + features.features[13]);
      System.out.println("indriInlink:" + features.features[14]);
      System.out.println("overlapInlink:" + features.features[15]);
      */
    }


  }

  public static void disableFeatures(String featureDisable){

    String[] words = featureDisable.split(",");
    for (String feature : words){
      featuresDisable.add(Integer.parseInt(feature));
    }
  }

  public static ArrayList<ArrayList<String>> generateQueries(String filename){
    ArrayList<ArrayList<String>> queries = new ArrayList<ArrayList<String>>();
    try {
      FileReader fileReader = new FileReader(filename);
      BufferedReader bufferedReader = new BufferedReader(fileReader);
      String line;
      while((line = bufferedReader.readLine()) != null) {
        queries.add(parseQuery(line));
      }
      bufferedReader.close();
      fileReader.close();
    }
    catch(Exception ex) {
      ex.printStackTrace();
    }
    return queries;
  }


  public static ArrayList<QDFeatures> readRelevanceJudgement(String filename, String currentQid, List<String> queryWords){
    String line;
    String[] words;
    ArrayList<QDFeatures> featureList = new ArrayList<QDFeatures>();
    try {
      FileReader fileReader = new FileReader(filename);

      BufferedReader bufferedReader = new BufferedReader(fileReader);

      while((line = bufferedReader.readLine()) != null) {//per doc
        words = line.split(" ");
        String qid = words[0];

        if (qid.equals(currentQid)){
          String docName = words[2];
          String relevance = words[3];
          int internalDocid = Idx.getInternalDocid(docName);
          if (internalDocid < 0){
            throw new Exception("No internal docid found!");
          }
          //create new feature vector
          QDFeatures qd = new QDFeatures(Integer.parseInt(qid), queryWords, internalDocid, BM25k1, BM25b, BM25k3, mu, lambda, relevance);
          featureList.add(qd);
        }



      }
      bufferedReader.close();
      fileReader.close();
    }
    catch(Exception ex) {
      ex.printStackTrace();
    }
    return featureList;
  }

  public static void readPageRank(String filename){
    String line;
    String[] words;
    int internalDocid;
    try {
      FileReader fileReader = new FileReader(filename);

      BufferedReader bufferedReader = new BufferedReader(fileReader);

      while((line = bufferedReader.readLine()) != null) {
        words = line.split("\t");
        //System.out.println(words[0]);
        internalDocid = Idx.getInternalDocid(words[0]);
        pageRank.put(internalDocid, Double.parseDouble(words[1]));


      }
      bufferedReader.close();
      fileReader.close();
    }
    catch(Exception ex) {
      ex.printStackTrace();
    }
  }



  /**
   * Return a query tree that corresponds to the query.
   * 
   * @param qString
   *          A string containing a query.
   *
   * @throws IOException Error accessing the Lucene index.
   */
  static ArrayList<String> parseQuery(String qString) throws IOException {
    ArrayList<String> terms = new ArrayList<String>();
    int begin = qString.indexOf(":");

    if (begin < 0){
      System.out.println("ERROR: no ':' in query!");
      throw new IOException("ERROR: no ':' in query!");
    }

    terms.add(qString.substring(0,begin));//add qid
    String words[] = qString.substring(begin + 1).split(" ");
    for (String word : words){//add terms
      if (word != null && word.length() > 0){
        String[] tmpList = tokenizeQuery(word);
        for (String tmp : tmpList){
          terms.add(tmp);
        }
      }
    }
    return terms;
  }


  static void printResults() throws IOException {



    double score = 0;
    int count = 0;
    String line = null;
    FileReader fileReader = new FileReader(testingDocumentScores);
    BufferedReader bufferedReader = new BufferedReader(fileReader);

    for (ArrayList<QDFeatures> query : QDs) {
      for (QDFeatures features : query) {
        line = bufferedReader.readLine();
        features.score = Double.parseDouble(line);
      }
    }
/*
    while((line = bufferedReader.readLine()) != null) {

      count++;
      if (!line.startsWith("nan")){
        score = Double.parseDouble(line);
      } else {
        score = 0;
      }
      QDs.get((int) (count / 100)).get(count % 100).score = score;
    }
*/
    bufferedReader.close();

    DecimalFormat formatter = new DecimalFormat("#0.000000000000");

    for (ArrayList<QDFeatures> query : QDs){//per query
      Collections.sort(query, new ScoreComparator());
    }
    PrintWriter writer = new PrintWriter(output);

    for (ArrayList<QDFeatures> query : QDs) {
      int i=0;
      for (QDFeatures features : query) {

        writer.println(features.qid + "\tQ0\t" + Idx.getExternalDocid(features.docid) + "\t"
                + (i + 1) + "\t" + formatter.format(features.score) + "\trun-1");
        writer.flush();
        i++;
      }
    }


  }

  /**
   * Read the specified parameter file, and confirm that the required
   * parameters are present.  The parameters are returned in a
   * HashMap.  The caller (or its minions) are responsible for
   * processing them.
   * @return The parameters, in <key, value> format.
   */
  private static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();

    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();

    return parameters;
  }

  /**
   * Given a query string, returns the terms one at a time with stopwords
   * removed and the terms stemmed using the Krovetz stemmer.
   * 
   * Use this method to process raw query terms.
   * 
   * @param query
   *          String containing query
   * @return Array of query tokens
   * @throws IOException Error accessing the Lucene index.
   */
  static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp =
      ANALYZER.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute =
      tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();

    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }

    return tokens.toArray (new String[tokens.size()]);
  }



}

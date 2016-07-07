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

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  private static final EnglishAnalyzerConfigurable ANALYZER =
    new EnglishAnalyzerConfigurable(Version.LUCENE_43);
  private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink", "keywords" };

  public static int opnum = 0;

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
      throw new IllegalArgumentException (USAGE);
    }

    Map<String, String> parameters = readParameterFile (args[0]);

    //  Configure query lexical processing to match index lexical
    //  processing.  Initialize the index and retrieval model.

    ANALYZER.setLowercase(true);
    ANALYZER.setStopwordRemoval(true);
    ANALYZER.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);

    Idx.initialize(parameters.get("indexPath"));
    RetrievalModel model = initializeRetrievalModel (parameters);

    //  Perform experiments.

    String output = parameters.get("trecEvalOutputPath");
    if ((!parameters.containsKey("fb")) || (parameters.get("fb").equalsIgnoreCase("false"))) {
      processQueryFile(parameters.get("queryFilePath"), model, output);
    } else{
    // query expansion
      ArrayList<ArrayList<DocScore>> initialResult;
      ArrayList<String> qids = new ArrayList<>();
      int fbDocs = Integer.parseInt(parameters.get("fbDocs"));
      int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
      int fbMu = 0;
      if (parameters.containsKey("fbMu")) {
        fbMu = Integer.parseInt(parameters.get("fbMu"));
      }
      double fbOrigWeight = 0.5;
      if (parameters.containsKey("fbOrigWeight")) {
        fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
      }
      String fbExpansionQueryFile = null;
      boolean writeExpansion = false;

      if (parameters.containsKey("fbExpansionQueryFile")) {// whether write expansion queries
        writeExpansion = true;
        fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");
      }

      if (parameters.containsKey("fbInitialRankingFile")){// get initial ranking file
        initialResult = readInitial(parameters.get("fbInitialRankingFile"), fbDocs, qids);
      } else {
        initialResult = processQueryFile(parameters.get("queryFilePath"), model, fbDocs, qids);
        //System.out.println("initial result head:" + initialResult.get(0).get(0).docid + " " + initialResult.get(0).get(0).score);
      }
      //query expansion
      ArrayList<String> expandedQueries = queryExpansion(initialResult, fbTerms, fbMu, fbDocs,
              writeExpansion, fbExpansionQueryFile, qids);

      //process expanded queries
      processQueryFile(parameters.get("queryFilePath"), expandedQueries, fbOrigWeight, model, output);


    }
    //  Clean up.

    timer.stop();
    // System.out.println ("Time:  " + timer);
  }

  private static ArrayList<String> queryExpansion (ArrayList<ArrayList<DocScore>> initialResult, int fbTerms,
                                                  int fbMu, int fbDocs, boolean writeExpansion,
                                                  String fbExpansionQueryFile, ArrayList<String> qids)
          throws IOException{
    //System.out.println("fbMu: " + fbMu);

    DecimalFormat formatter = new DecimalFormat("#0.000000000000");
    ArrayList<String> expandedQueries = new ArrayList<String>();
    HashMap<String, Double> termScoreMap;
    HashMap<String, Integer> termCTF;
    ArrayList<TermScore> termList;
    ArrayList<TermVector> tvs = new ArrayList<TermVector>();
    int qnum;
    for (qnum=0; qnum < initialResult.size(); qnum++){//for each query
      ArrayList<DocScore> docList = initialResult.get(qnum);

      if (docList.size() != fbDocs){
        System.out.println("docList size not equals to  fbDocs -_- !!");
      }

      termScoreMap = new HashMap<String, Double>();
      termCTF = new HashMap<String, Integer>();
      termList = new ArrayList<TermScore>();
      int i;
      String term;
      double score;
      double prevscore;
      double ptd;
      double ctf;
      double cLength = (double)Idx.getSumOfFieldLengths("body");;
      for (DocScore doc : docList){//for each doc
        //System.out.println(doc.docid + " " + doc.score);
        try {
          TermVector termVector = new TermVector(doc.docid, "body");
          tvs.add(termVector);
          int len = termVector.stemsLength();
          for (i=1; i<len; i++){//0 is stopwords
            term = termVector.stemString(i).toLowerCase();
            //System.out.print(term + " ");
            //skip '.' and ','
            if ((term.indexOf('.') >= 0) || (term.indexOf(',') >= 0) ){
              continue;
            }
            //compoute score
            ctf = termVector.totalStemFreq(i);
            if (!termCTF.containsKey(term)){//store ctf
              termCTF.put(term, (int)ctf);
            }
            ptd = (termVector.stemFreq(i) + (double)fbMu * ctf / cLength) / (termVector.positionsLength() + (double)fbMu);
            score = ptd * doc.score * Math.log(cLength / ctf);
            //System.out.println(score);



            //update score
            if (termScoreMap.containsKey(term)){
              prevscore = termScoreMap.get(term);
              termScoreMap.put(term, prevscore + score);
            } else {
              termScoreMap.put(term, score);
              termList.add(new TermScore(term, 0));
            }
          }


        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      //get final scores
      for (TermScore termScore : termList){
        ctf = termCTF.get(termScore.term);
        // update default score
        for (i=0; i < fbDocs; i++){
          TermVector termVector = tvs.get(i);
          if (termVector.indexOfStem(termScore.term) == -1){
            ptd = ((double)fbMu * ctf / cLength) / (termVector.positionsLength() + (double)fbMu);
            score = ptd * docList.get(i).score * Math.log(cLength / ctf);
            prevscore = termScoreMap.get(termScore.term);
            termScoreMap.put(termScore.term, prevscore + score);
          }

        }
        termScore.score = termScoreMap.get(termScore.term);
      }

      termList.sort(new TermScore());
      //System.out.println(termList.toString());
      termList = new ArrayList<TermScore> (termList.subList(0, fbTerms));
      //System.out.println(termList.toString());
      StringBuilder expandedQuery = new StringBuilder("#wand (");
      for (TermScore termScore : termList){
        expandedQuery.append(" " + formatter.format(termScore.score) + " " + termScore.term);
      }
      expandedQuery.append(")");
      String tmp = new String(expandedQuery.toString());
      //System.out.println(tmp);
      expandedQueries.add(tmp);


    }

    // write expanded queries
    if (writeExpansion){
      PrintWriter writer = new PrintWriter(fbExpansionQueryFile);
      for (qnum = 0; qnum < expandedQueries.size() ;qnum++){
        String tmpstr = qids.get(qnum) + ": " + expandedQueries.get(qnum);
        writer.println(tmpstr);
      }
      writer.close();
    }

    return expandedQueries;
  }

// return a list of top n docids for each original query
  private static ArrayList<ArrayList<DocScore>> readInitial (String filename, int fbDocs, ArrayList<String> qids)
          throws Exception {
    ArrayList<ArrayList<DocScore>> initialResult = new ArrayList<ArrayList<DocScore>> ();
    ArrayList<DocScore> tmpList = new ArrayList<DocScore>();;
    BufferedReader input = null;
    String qLine = null;
    input = new BufferedReader(new FileReader(filename));
    String queryID = null;
    int count = 0;
    String prevID = null;
    String[] wordList;
    String docid;
    double score;
    while ((qLine = input.readLine()) != null) {// add top fdDocs docs to list
      wordList = qLine.split(" ");
      queryID = wordList[0];
      docid = wordList[2];
      score = Double.parseDouble(wordList[4]);
      //System.out.println(queryID);
      if ((prevID == null) || (!queryID.equals(prevID))) {// a new query
        qids.add(queryID);
        //store new list
        tmpList = new ArrayList<>();
        initialResult.add(tmpList);
        //System.out.println("readInitial put " + queryID);
        count = 0;
      }
      if (count < fbDocs){
        tmpList.add(new DocScore(Idx.getInternalDocid(docid), score));
      }
      count++;
      prevID = queryID;
    }

    /*
    //store the last list
    initialResult.add(tmpList);
    System.out.println("readInitial put " + queryID);
    */

    input.close();

    return initialResult;
  }

  /**
   * Allocate the retrieval model and initialize it using parameters
   * from the parameter file.
   * @return The initialized retrieval model
   * @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
    }else if (modelString.equals("rankedboolean")) {
      model = new RetrievalModelRankedBoolean();
    }else if (modelString.equals("bm25")) {
      model = new RetrievalModelBM25();
      // BM25 parameters
      String BM25k1 = parameters.get("BM25:k_1");
      String BM25b = parameters.get("BM25:b");
      String BM25k3 = parameters.get("BM25:k_3");
      ((RetrievalModelBM25)model).setK1(Double.parseDouble(BM25k1));
      ((RetrievalModelBM25)model).setB(Double.parseDouble(BM25b));
      ((RetrievalModelBM25)model).setK3(Double.parseDouble(BM25k3));
    }else if (modelString.equals("indri")) {
      model = new RetrievalModelIndri();
      String mu = parameters.get("Indri:mu");
      String lambda = parameters.get("Indri:lambda");
      ((RetrievalModelIndri) model).setLambda(Double.parseDouble(lambda));
      ((RetrievalModelIndri) model).setMu(Double.parseDouble(mu));
    }
    else {
      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }

    return model;
  }

  /**
   * Return a query tree that corresponds to the query.
   * 
   * @param qString
   *          A string containing a query.
   *
   * @throws IOException Error accessing the Lucene index.
   */
  static Qry parseQuery(String qString, RetrievalModel model) throws IOException {

    //  Add a default query operator to every query. This is a tiny
    //  bit of inefficiency, but it allows other code to assume
    //  that the query will return document ids and scores.

    String defaultOp = model.defaultQrySopName();
    qString = defaultOp + "(" + qString + ")";

    //  Simple query tokenization.  Terms like "near-death" are handled later.

    StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
    String token = null;


    //  This is a simple, stack-based parser.  These variables record
    //  the parser's state.
    
    Qry currentOp = null;
    Stack<Qry> opStack = new Stack<Qry>();
    boolean weightExpected = false;
    Stack<Double> weightStack = new Stack<Double>();
    int tmp;
    boolean isweight;
    double weight = 1d;
    //  Each pass of the loop processes one token. The query operator
    //  on the top of the opStack is also stored in currentOp to
    //  make the code more readable.



    while (tokens.hasMoreTokens()) {



      token = tokens.nextToken();

      isweight = false;

      //System.out.println("token:" + token);

      if (token.matches("[ ,(\t\n\r]")) {
        continue;
      } else if (token.equals(")")) {	// Finish current query op.

        // If the current query operator is not an argument to another
        // query operator (i.e., the opStack is empty when the current
        // query operator is removed), we're done (assuming correct
        // syntax - see below).

        opStack.pop();

        if (opStack.empty())
          break;

	// Not done yet.  Add the current operator as an argument to
        // the higher-level operator, and shift processing back to the
        // higher-level operator.

        Qry arg = currentOp;
        currentOp = opStack.peek();
	    currentOp.appendArg(arg);
        if (currentOp.weightExpected) {
          weight = weightStack.pop();
          currentOp.weights.add(weight);
        }

      } else if (token.equalsIgnoreCase("#and")) {
        weightExpected = false;
        currentOp = new QrySopAnd ();
        currentOp.weightExpected = weightExpected;
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else if (token.equalsIgnoreCase("#wand")) {
        weightExpected = true;
        currentOp = new QrySopWAnd ();
        currentOp.weightExpected = weightExpected;
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else if (token.equalsIgnoreCase("#wsum")) {
        weightExpected = true;
        currentOp = new QrySopWSum ();
        currentOp.weightExpected = weightExpected;
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else if (token.equalsIgnoreCase("#or")) {
        weightExpected = false;
        currentOp = new QrySopOr ();
        currentOp.weightExpected = weightExpected;
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else if (token.equalsIgnoreCase("#sum")) {
        weightExpected = false;
        currentOp = new QrySopSum ();
        currentOp.weightExpected = weightExpected;
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else if ((token.toLowerCase()).startsWith("#near")) {
        weightExpected = false;
        currentOp = new QryIopNear ();
        currentOp.weightExpected = weightExpected;
        int index = token.indexOf('/');
        currentOp.para = Integer.parseInt(token.substring(index + 1));
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else if ((token.toLowerCase()).startsWith("#window")) {
        weightExpected = false;
        currentOp = new QryIopWindow ();
        currentOp.weightExpected = weightExpected;
        int index = token.indexOf('/');
        currentOp.para = Integer.parseInt(token.substring(index + 1));
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else if (token.equalsIgnoreCase("#syn")) {
        currentOp = new QryIopSyn();
        currentOp.weightExpected = weightExpected;
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else {

        if(currentOp.weightExpected && ((tmp = token.indexOf('.')) > 0)){
          isweight = true;
          for (int k=0; k<token.length(); k++){
            if (k!=tmp && !Character.isDigit(token.charAt(k))){
              isweight = false;
              break;
            }
          }

          if (isweight){
            //currentOp.weights.add(Double.parseDouble(token));
            weightStack.push(Double.parseDouble(token));
            //System.out.println(currentOp.getDisplayName() + " " + Double.parseDouble(token));
            continue;
          }

        }





        //  Split the token into a term and a field.

        int delimiter = token.indexOf('.');
        String field = null;
        String term = null;

        if (delimiter < 0) {
          field = "body";
          term = token;
        } else {
          field = token.substring(delimiter + 1).toLowerCase();
          term = token.substring(0, delimiter);
        }

        if ((field.compareTo("url") != 0) &&
	    (field.compareTo("keywords") != 0) &&
	    (field.compareTo("title") != 0) &&
	    (field.compareTo("body") != 0) &&
            (field.compareTo("inlink") != 0)) {
          throw new IllegalArgumentException ("Error: Unknown field " + token);
        }

        //  Lexical processing, stopwords, stemming.  A loop is used
        //  just in case a term (e.g., "near-death") gets tokenized into
        //  multiple terms (e.g., "near" and "death").


        if (currentOp.weightExpected){
          weight = weightStack.pop();
        }

        String t[] = tokenizeQuery(term);

        for (int j = 0; j < t.length; j++) {
          //System.out.println(t[j]);
          Qry termOp = new QryIopTerm(t [j], field);
          if (currentOp.weightExpected) {
            currentOp.weights.add(weight);
          }
	      currentOp.appendArg (termOp);
	    }
      }
    }


    //  A broken structured query can leave unprocessed tokens on the opStack,

    if (tokens.hasMoreTokens()) {
      throw new IllegalArgumentException
        ("Error:  Query syntax is incorrect.  " + qString);
    }

    return currentOp;
  }

  /**
   * Remove degenerate nodes produced during query parsing, for
   * example #NEAR/1 (of the) that can't possibly match. It would be
   * better if those nodes weren't produced at all, but that would
   * require a stronger query parser.
   */
  static boolean parseQueryCleanup(Qry q) {

    boolean queryChanged = false;

    // Iterate backwards to prevent problems when args are deleted.

    for (int i = q.args.size() - 1; i >= 0; i--) {

      Qry q_i = q.args.get(i);

      // All operators except TERM operators must have arguments.
      // These nodes could never match.
      
      if ((q_i.args.size() == 0) &&
	  (! (q_i instanceof QryIopTerm))) {
        q.removeArg(i);
        queryChanged = true;
      } else 

	// All operators (except SCORE operators) must have 2 or more
	// arguments. This improves efficiency and readability a bit.
	// However, be careful to stay within the same QrySop / QryIop
	// subclass, otherwise the change might cause a syntax error.
	
	if ((q_i.args.size() == 1) &&
	    (! (q_i instanceof QrySopScore))) {

	  Qry q_i_0 = q_i.args.get(0);

	  if (((q_i instanceof QrySop) && (q_i_0 instanceof QrySop)) ||
	      ((q_i instanceof QryIop) && (q_i_0 instanceof QryIop))) {
	    q.args.set(i, q_i_0);
	    queryChanged = true;
	  }
	} else

	  // Check the subtree.
	  
	  if (parseQueryCleanup (q_i))
	    queryChanged = true;
    }

    return queryChanged;
  }

  /**
   * Print a message indicating the amount of memory used. The caller
   * can indicate whether garbage collection should be performed,
   * which slows the program but reduces memory usage.
   * 
   * @param gc
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {

    Qry q = parseQuery(qString, model);


    // Optimize the query.  Remove query operators (except SCORE
    // operators) that have only 1 argument. This improves efficiency
    // and readability a bit.

    if (q.args.size() == 1) {
      Qry q_0 = q.args.get(0);

      if (q_0 instanceof QrySop) {
	q = q_0;
      }
    }

    while ((q != null) && parseQueryCleanup(q))
      ;

    // Show the query that is evaluated

    // test
    //System.out.println("    --> " + q);
    //System.out.println(((double)Idx.getSumOfFieldLengths("body"))/(double)Idx.getDocCount("body"));

    if (q != null) {

      ScoreList r = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);

        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();
          double score = ((QrySop) q).getScore (model);
          r.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }
      r.sort();
      return r;
    } else
      return null;
  }

  /**
   * Process the query file.
   * @param queryFilePath
   * @param model
   * @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(String queryFilePath,
                               RetrievalModel model, String output)
      throws IOException {

    BufferedReader input = null;
    PrintWriter writer = new PrintWriter(output);

    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));

      //  Each pass of the loop processes one query.

      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        //printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);

        //System.out.println("Query " + qLine);

        ScoreList r = null;

        r = processQuery(query, model);

        if (r != null) {
          printResults(qid, r, writer);
          //System.out.println();
        }
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
      writer.close();
    }
  }

  static void processQueryFile(String queryFilePath, ArrayList<String> expandedQueries, double fbOrigWeight,
                               RetrievalModel model, String output)
          throws IOException {
    BufferedReader input = null;
    PrintWriter writer = new PrintWriter(output);

    try {
      int i = 0;



      String qLine = null;
      String addedLine = null;
      input = new BufferedReader(new FileReader(queryFilePath));

      //  Each pass of the loop processes one query.

      while ((qLine = input.readLine()) != null) {

        addedLine = expandedQueries.get(i);
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
                  ("Syntax error:  Missing ':' in query line.");
        }

        //printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        //the combined new query
        String query = "#wand ( " + fbOrigWeight + " #and ( " + qLine.substring(d + 1) +
                " ) " + Double.toString(1-fbOrigWeight) + " " + addedLine + " )";

        //System.out.println("Query " + query);

        ScoreList r = null;

        r = processQuery(query, model);

        if (r != null) {
          printResults(qid, r, writer);
          //System.out.println();
        }
        i++;
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      writer.close();
    }
  }

  static ArrayList<ArrayList<DocScore>> processQueryFile(String queryFilePath,
                               RetrievalModel model, int fbDocs, ArrayList<String> qids)
          throws IOException {

    BufferedReader input = null;
    ArrayList<ArrayList<DocScore>> initialResult = new ArrayList<ArrayList<DocScore>> ();
    ArrayList<DocScore> tmpList;

    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));

      //  Each pass of the loop processes one query.
      while ((qLine = input.readLine()) != null) {
        tmpList = new ArrayList<>();
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
                  ("Syntax error:  Missing ':' in query line.");
        }

        //printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        qids.add(qid);
        String query = qLine.substring(d + 1);

        //System.out.println("Query " + qLine);

        ScoreList r = null;

        r = processQuery(query, model);

        if (r != null) {
          for (int i = 0; i < fbDocs && i < r.size(); i++) {
            tmpList.add(new DocScore(r.getDocid(i), r.getDocidScore(i)));
            //System.out.println("docid: " + r.getDocid(i) + "doc score: " + r.getDocidScore(i));
          }

        }
        initialResult.add(tmpList);
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
    }
    return initialResult;
  }
  /**
   * Print the query results.
   * 
   * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
   * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */
  static void printResults(String queryName, ScoreList result, PrintWriter writer) throws IOException {

    DecimalFormat formatter = new DecimalFormat("#0.000000000000");
    //System.out.println(queryName + ":  ");
    if (result.size() < 1) {
      writer.println(queryName + "\tQ0\t" + "dummy\t1\t0\t" + "run-1");
    } else {
      for (int i = 0; i < 100 && i < result.size(); i++) {
        writer.println(queryName + "\tQ0\t" + Idx.getExternalDocid(result.getDocid(i)) + "\t"
                + (i+1) + "\t" + formatter.format(result.getDocidScore(i)) + "\trun-1");
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

    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath") &&
           parameters.containsKey ("retrievalAlgorithm"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }

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

/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
      return this.getScoreRankedBoolean (r);
    } else if (r instanceof RetrievalModelBM25) {
      return this.getScoreBM25 ((RetrievalModelBM25)r);
    } else if (r instanceof RetrievalModelIndri) {
      return this.getScoreIndri ((RetrievalModelIndri)r);
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }

  /**
   *  getScore for the Ranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      int docindex = ((QryIop) this.args.get(0)).getDocIteratorIndex();

      double tf = (double) (((QryIop) this.args.get(0)).invertedList.getTf(docindex));
      //System.out.print(q.invertedList.field + "\t");
      //System.out.print("docid" + docid + "\t");
      //System.out.print(tf + "\t");
      //System.out.println();
      //q.invertedList.print();
      return tf;
    }
  }

  public double getScoreBM25 (RetrievalModelBM25 r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      int docid = this.docIteratorGetMatch();
      int docindex = ((QryIop) this.args.get(0)).getDocIteratorIndex();
      double tf = (double) (((QryIop) this.args.get(0)).invertedList.getTf(docindex));


      String field = this.getField(r);
      double docLength = Idx.getFieldLength(field, docid);
      double N = Idx.getNumDocs();
      double avgDocLength = ((double)Idx.getSumOfFieldLengths(field))/(double)Idx.getDocCount(field);
      double k1 = r.getK1();
      double b = r.getB();
      double df = this.getDF(r);
      double rsj = 0;
      if (df < N/2){
        rsj = Math.log((N - df + 0.5) / (df + 0.5));
      }
      double tfweight = tf/(tf+k1*(1-b+b*docLength/avgDocLength));
      return rsj*tfweight;
    }
  }


  public double getScoreIndri (RetrievalModelIndri r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      int docid = this.docIteratorGetMatch();
      int docindex = ((QryIop) this.args.get(0)).getDocIteratorIndex();
      double ctf = this.getCtf(r);
      double tf = (double) (((QryIop) this.args.get(0)).invertedList.getTf(docindex));
      String field = this.getField(r);
      double docLength = Idx.getFieldLength(field, docid);
      double cLength = (double)Idx.getSumOfFieldLengths(field);
      double pMLEc = ctf/cLength;
      double p = (1 - r.getLambda()) * (tf + r.getMu() * pMLEc) / (docLength + r.getMu())
              + r.getLambda() * pMLEc;

      return p;
    }
  }

  public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
    if (r instanceof RetrievalModelIndri) {
      RetrievalModelIndri modelIndri = (RetrievalModelIndri)r;

      double ctf = this.getCtf(r);
      double tf = 0;
      String field = this.getField(r);
      double docLength = Idx.getFieldLength(field, docid);
      double cLength = (double) Idx.getSumOfFieldLengths(field);
      double pMLEc = ctf / cLength;
      double p = (1 - modelIndri.getLambda()) * (tf + modelIndri.getMu() * pMLEc) / (docLength + modelIndri.getMu())
                + modelIndri.getLambda() * pMLEc;
      return p;
    }else{
      return 0.0;
    }
  }
  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }

  public String getField(RetrievalModel r) throws IOException {
    String field = ((QryIop) this.args.get(0)).getField();
    return field;
  }
  public int getDF(RetrievalModel r) throws IOException {
    return ((QryIop) this.args.get(0)).getDf();
  }
  public int getCtf(RetrievalModel r) throws IOException {
    return ((QryIop) this.args.get(0)).getCtf();
  }
}

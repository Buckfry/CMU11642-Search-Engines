/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The And operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        if (r instanceof RetrievalModelIndri) {
            return this.docIteratorHasMatchMin(r);
        }
        else {
            return this.docIteratorHasMatchAll(r);
        }
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean(r);
        } else if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean(r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            /* debug
            for (Qry q_i: this.args) {
                System.out.print(q_i.docIteratorGetMatch() + "\t");
            }
            System.out.println();
            /* debug */
            return 1.0;

        }
    }

    /**
     *  getScore for the RankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            // compute score
            // inputs are scores
            double minscore = Double.POSITIVE_INFINITY;
            double scorecache;
            for (Qry q_i: this.args) {
                scorecache = ((QrySop) q_i).getScore(r);
                //System.out.print(q_i.docIteratorGetMatch() + "\t");
                if (scorecache < minscore) {
                    minscore = scorecache;
                }
            }
            //System.out.println();
            return minscore;
        }
    }
    public double getScoreIndri (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            int docid = this.docIteratorGetMatch();
            double score = 1d;
            double len = this.args.size();
            for (Qry q_i : this.args) {
                if (!q_i.docIteratorHasMatchCache())
                    score *= ((QrySop) q_i).getDefaultScore(r, docid);
                else if (q_i.docIteratorGetMatch() == docid)
                    score *= ((QrySop) q_i).getScore(r);
                else
                    score *= ((QrySop) q_i).getDefaultScore(r, docid);
            }
            score = Math.pow(score, 1d / len);
            return score;
        }

    }
    public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            double score = 1d;
            double len = this.args.size();
            for (Qry q_i : this.args){
                score *= ((QrySop) q_i).getDefaultScore(r, docid);
            }
            score = Math.pow(score, 1d/len);
            return score;
        }else{
            return 0.0;
        }
    }
}

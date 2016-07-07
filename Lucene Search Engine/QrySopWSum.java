/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

/**
 *  The WSum operator for all retrieval models.
 */
public class QrySopWSum extends QrySop {

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

        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WAND operator.");
        }
    }


    public double getScoreIndri (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            int docid = this.docIteratorGetMatch();
            double totalscore = 0d;
            double score = 1d;
            double len = this.args.size();
            double sumweight = 0d;

            for (int i=0; i<len; i++) {
                sumweight += this.weights.get(i);
                //System.out.println(i + " " + this.weights.get(i));
            }

            for (int i=0; i<len; i++) {
                Qry q_i = this.args.get(i);
                if (!q_i.docIteratorHasMatchCache())
                    score = ((QrySop) q_i).getDefaultScore(r, docid);
                else if (q_i.docIteratorGetMatch() == docid)
                    score = ((QrySop) q_i).getScore(r);
                else
                    score = ((QrySop) q_i).getDefaultScore(r, docid);
                score = score * this.weights.get(i) / sumweight;
                totalscore += score;
            }

            return totalscore;
        }

    }
    public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            double sumweight = 0d;
            double score = 1d;
            double len = this.args.size();
            double totalscore = 0d;

            for (int i=0; i<len; i++) {
                sumweight += this.weights.get(i);
            }

            for (int i=0; i<len; i++) {
                QrySop q_i = (QrySop)this.args.get(i);
                score = q_i.getDefaultScore(r, docid) * this.weights.get(i) / sumweight;
                totalscore += score;
            }
            return totalscore;
        }else{
            return 0.0;
        }
    }
}

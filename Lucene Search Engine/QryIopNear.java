/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/**
 *  The NEAR/n operator for all retrieval models.
 */
public class QryIopNear extends QryIop {

  /**
   *  Evaluate the query operator; the result is an internal inverted
   *  list that may be accessed via the internal iterators.
   *  @throws IOException Error accessing the Lucene index.
   */



  protected void evaluate () throws IOException {

    //  Create an empty inverted list.  If there are no query arguments,
    //  that's the final result.
    
    this.invertedList = new InvList (this.getField());

    if (args.size () == 0) {
      return;
    }
    //  If arguments have no same DocID, we are done
    if (!this.docIteratorHasMatchAll(null)){
      return;
    }

    //  Each pass of the loop adds 1 document to result inverted list
    //  until any of the argument inverted lists is depleted.

    // If any document id is INVALID, we are done.
    boolean flagdone = false;

    while (!flagdone) {


      for (Qry q_i: this.args) {
        if (!q_i.docIteratorHasMatch (null)) {
          flagdone = true;
        }else {
          int q_iDocid = q_i.docIteratorGetMatch ();
          if (q_iDocid == Qry.INVALID_DOCID)
            flagdone = true;
        }
      }

      if (flagdone)
        break;				// A docid has been depleted.  Done.
      //  Note:  This implementation assumes that a location will not appear
      //  in two or more arguments.  #SYN (apple apple) would break it.
      // find the minimum docid, and find if all args have the same docid
      int minDocid = this.args.get(0).docIteratorGetMatch();

      // debug

      int minindex = 0;
      boolean flagsamedocid = true;

      for (int i = 0; i < this.args.size(); i++) {
        if (this.args.get(i).docIteratorGetMatch() != minDocid)
          flagsamedocid = false;
        if (this.args.get(i).docIteratorGetMatch() < minDocid) {
          minDocid = this.args.get(i).docIteratorGetMatch();
          minindex = i;
        }
      }

      if (!flagsamedocid){//docids not same
        this.args.get(minindex).docIteratorAdvancePast(minDocid);
      } else{//same doc ids, try to find matches

        ArrayList<Integer> positions;// = new ArrayList<Integer>();


        //find all matches in this doc

        ArrayList<Integer> tmppositions;
        QryIop argi = (QryIop) this.args.get(0);

        positions = new ArrayList<Integer>((argi.docIteratorGetMatchPosting().positions));

        int tmploc1;
        int tmploc2;
        for (int i=1; i < this.args.size(); i++){
          argi = (QryIop) this.args.get(i);


          tmppositions = positions;

          positions = new ArrayList<Integer>();
          for(int j=0; j < tmppositions.size() && argi.locIteratorHasMatch();){
            tmploc1 = tmppositions.get(j);
            tmploc2 = argi.locIteratorGetMatch();
            if (tmploc1 > tmploc2){
              argi.locIteratorAdvance();
            } else if (tmploc2 - tmploc1 > this.para){
              j++;
            } else{// match
              positions.add(tmploc2);
              j++;
              argi.locIteratorAdvance();
            }
          }
        }
        if (! positions.isEmpty()){
          this.invertedList.appendPosting(minDocid, positions);
        }


        //advance all doc iterators
        for (Qry q_i: this.args){
          q_i.docIteratorAdvancePast(minDocid);
        }
      }
    }

  }

}

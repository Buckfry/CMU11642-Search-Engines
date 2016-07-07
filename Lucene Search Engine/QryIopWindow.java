/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;

/**
 *  The NEAR/n operator for all retrieval models.
 */
public class QryIopWindow extends QryIop {

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

        ArrayList<Integer> positions = new ArrayList<Integer>();


        //find all matches in this doc

        QryIop argi;

        int tmploc;
        int maxloc;
        int minloc;
        int minlocindex;
        boolean flaglocvalid = true;
        while (flaglocvalid) {
          maxloc = 0;
          minloc = Integer.MAX_VALUE;
          minlocindex = -1;
          for (int i = 0; i < this.args.size(); i++) {
            argi = (QryIop) this.args.get(i);
            tmploc = argi.locIteratorGetMatch();
            if (tmploc > maxloc){
              maxloc = tmploc;
            }
            if (tmploc < minloc){
              minloc = tmploc;
              minlocindex = i;
            }

          }
          if ((1 + maxloc - minloc) > this.para){//no match
            argi = (QryIop) this.args.get(minlocindex);
            argi.locIteratorAdvance();
            if (!argi.locIteratorHasMatch()){
              break;
            }
          } else {//match
            positions.add(maxloc);
            for (Qry q_i : this.args) {
              ((QryIop)q_i).locIteratorAdvance();
              if (!(((QryIop)q_i).locIteratorHasMatch())){
                flaglocvalid = false;
                break;
              }
            }
          }
        }
        if (!positions.isEmpty()) {
          this.invertedList.appendPosting(minDocid, positions);
        }


        //advance all doc iterators
        for (Qry q_i : this.args) {
          q_i.docIteratorAdvancePast(minDocid);
        }
      }
    }

  }

}

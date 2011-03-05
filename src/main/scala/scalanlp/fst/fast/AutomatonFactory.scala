package scalanlp.fst.fast

import scalanlp.fst;

import fst.Alphabet
import scalanlp.math.Semiring
import scalanlp.util.{Encoder, Index, DenseIntIndex}
import scalala.tensor.sparse.SparseVector
import scalanlp.collection.mutable.SparseArray
import collection.immutable.BitSet


/**
 * 
 * @author dlwh
 */

class AutomatonFactory[T](val index: Index[T])
                          (implicit protected val ring: Semiring[Double],
                           protected val alphabet: Alphabet[T]) extends Distance[T]
                           with Composition[T] with DSL[T] with EditDistanceFactory[T] {
  val encoder = Encoder.fromIndex(index);
  val epsilonIndex = index(alphabet.epsilon);

  trait Automaton { outer =>
    // source -> character -> target -> weight
    def arcsFrom(s: Int):SparseArray[SparseVector]
    def arcsFrom(s:Int, ch: Int):SparseVector
    def numStates:Int
    def initialState: Int;
    def initialWeight: Double;
    def finalWeights: Array[Double];
    def finalWeight(s: Int) = finalWeights(s);
    def allStates = 0 until numStates;

    def &(a: Automaton) = intersect(this,a);

    override def toString = {

      val Eps = alphabet.epsilon;
      val Sig = alphabet.sigma;
      def transform(c: T) = c match {
        case Eps => "&epsilon;"
        case Sig => "&sigma;"
        case x => x;
      }

      val sb = new StringBuilder;
      sb ++= "digraph A {\n";

      for {
        s <- 0 until numStates iterator;
        (idx,targets) <- arcsFrom(s) iterator;
        (sink,weight) <- targets.activeElements
      } {
        sb ++= "    \"" + s + "\"->\"" + sink +"\"";
        sb ++= "[ label=\""+transform(index.get(idx))+"/" + weight +"\"]\n";
      }

      for(s <- allStates) {
        sb ++= "    \"" + s + "\"";
        sb ++= "[ label=\""+ s + " " + finalWeight(s) + "\"]\n";
      }
      sb ++= "}";
      sb.toString;
    }

    lazy val cost = if(false) { //if(isCyclic) {
      val costs = allPairDistances(this);
      var cost = ring.zero;
      for( (to,pathWeight) <- costs(initialState)) {
        cost = ring.plus(cost,ring.times(initialWeight,ring.times(pathWeight,finalWeight(to))));
      }
      cost;
    } else {
      val costs = singleSourceShortestDistances(this);
      var cost = ring.zero;
      for( (w,s) <- costs zipWithIndex) {
        cost = ring.plus(cost,ring.times(w,finalWeight(s)));
      }
      cost;
    }

    def asTransducer:Transducer = new Transducer {
      def numStates = outer.numStates;
      def arcsFrom(s: Int) = {
        val arcs = outer.arcsFrom(s)
        val r = encoder.fillSparseArray(encoder.fillSparseArray(mkSparseVector(numStates)))
        for( (ch,weights) <- arcs) {
          r.getOrElseUpdate(ch).update(ch,weights);
        }
        r
      }

      def arcsFrom(s: Int, ch1: Int, ch2: Int) = {
        if(ch1 != ch2) {
          mkSparseVector(numStates);
        } else {
          outer.arcsFrom(s,ch1);
        }
      }

      def arcsWithInput(s: Int, inCh: Int) = {
        val r = encoder.fillSparseArray(mkSparseVector(numStates));
        val arcs = outer.arcsFrom(s,inCh);
        r(inCh) = arcs;
        r
      }

      def arcsWithOutput(s: Int, outCh: Int) = {
        val r = encoder.fillSparseArray(mkSparseVector(numStates));
        val arcs = outer.arcsFrom(s,outCh);
        r(outCh) = arcs;
        r
      }

      def initialState = outer.initialState
      def initialWeight = outer.initialWeight;
      def finalWeights = outer.finalWeights;

      override def inputProjection = outer;
      override def outputProjection = outer;
    }
  }

  trait Transducer {
    // source -> inCh -> outCh -> -> target -> weight
    def arcsFrom(s: Int):SparseArray[SparseArray[SparseVector]]
    def arcsFrom(s:Int, ch1: Int, ch2: Int):SparseVector
    // outCh -> target
    def arcsWithInput(s: Int, inCh: Int):SparseArray[SparseVector];
    // inputCh -> target
    def arcsWithOutput(s: Int, outCh: Int):SparseArray[SparseVector];
    def numStates:Int
    def initialState: Int;
    def initialWeight: Double;
    def finalWeights: Array[Double];
    def finalWeight(s: Int) = finalWeights(s);
    def allStates = 0 until numStates;

    def inputProjection:Automaton = {
      val arcs = Array.tabulate(numStates){ s =>
        val r = encoder.fillSparseArray(mkSparseVector(numStates));
        for( (inCh,outs) <- arcsFrom(s); (outCh,weights) <- outs) {
          var i = 0;
          val dest = r.getOrElseUpdate(inCh);
          while(i < weights.used) {
            dest(weights.index(i)) = ring.plus(dest(weights.index(i)),weights.data(i));
            i+=1;
          }
        }
        r
      }

      automaton(arcs,finalWeights,initialState,initialWeight);

    }

    def outputProjection:Automaton = {
      val arcs = Array.tabulate(numStates){ s =>
        val r = encoder.fillSparseArray(mkSparseVector(numStates));
        for( (inCh,outs) <- arcsFrom(s); (outCh,weights) <- outs) {
          var i = 0;
          val dest = r.getOrElseUpdate(outCh);
          while(i < weights.used) {
            dest(weights.index(i)) = ring.plus(dest(weights.index(i)),weights.data(i));
            i+=1;
          }
        }
        r
      }

      automaton(arcs,finalWeights,initialState,initialWeight);

    }


    override def toString = {
      val Eps = alphabet.epsilon;
      val Sig = alphabet.sigma;
      def transform(c: T) = c match {
        case Eps => "&epsilon;"
        case Sig => "&sigma;"
        case x => x;
      }

      val sb = new StringBuilder;
      sb ++= "digraph A {\n";

      for {
        s <- 0 until numStates iterator;
        (inIdx,outputs) <- arcsFrom(s) iterator;
        (outIdx,targets) <- outputs;
        (sink,weight) <- targets.activeElements
      } {
        sb ++= "    \"" + s + "\"->\"" + sink +"\"";
        sb ++= "[ label=\""+transform(index.get(inIdx))+":" + transform(index.get(outIdx)) + "/" + weight +"\"]\n";
      }

      for(s <- allStates) {
        sb ++= "    \"" + s + "\"";
        sb ++= "[ label=\""+ s + " " + finalWeight(s) + "\"]\n";
      }
      sb ++= "}";
      sb.toString;
    }

  }


  def constant(seq: Seq[T], weight: Double = ring.one) = automaton(seq,weight);

  def automaton(seq: Seq[T], weight: Double):Automaton = {
    val numStates = seq.length + 1;
    val arcs = Array.fill(numStates)(encoder.fillSparseArray {
      mkSparseVector(numStates)
    });
    for((ch,i) <- seq.zipWithIndex) {
      arcs(i).getOrElseUpdate(index(ch))(i+1) = ring.one;
    }
    val finalWeights = Array.fill(numStates)(ring.zero);
    finalWeights(numStates-1) = ring.one;
    automaton(arcs, finalWeights, startWeight = weight);
  }

  def automaton(arcs: Array[SparseArray[SparseVector]], endWeights: Array[Double],
               startState: Int = 0, startWeight: Double = ring.one):Automaton = new Automaton {
    def allArcs = arcs;
    def numStates = arcs.length;
    def arcsFrom(s:Int) = allArcs(s);
    def arcsFrom(s:Int, ch: Int) = allArcs(s)(ch);
    def initialState = startState
    def initialWeight: Double = startWeight;
    def finalWeights = endWeights;
  }


  def transducer(arcs: Array[SparseArray[SparseArray[SparseVector]]],
                 endWeights: Array[Double],
                 startState:Int =0,
                 startWeight: Double = ring.one):Transducer = {
    lazy val arcsByOutput: Array[SparseArray[SparseArray[SparseVector]]] = arcs.map { arcs =>
      // this is basically an elaborate transpose operation
      val result = encoder.fillSparseArray(encoder.fillSparseArray{
        val vec = new SparseVector(arcs.length);
        vec.default = ring.zero
        vec
      });
      for( (ch1,outs) <- arcs; (ch2, targets) <- outs) {
        result.getOrElseUpdate(ch2).update(ch1,targets);
      }
      result;
    }

    new Transducer {
      def finalWeights = endWeights

      def initialWeight = startWeight;

      def initialState = startState;

      def numStates = arcs.length;

      def arcsWithOutput(s: Int, outCh: Int) = arcsByOutput(s)(outCh);

      def arcsWithInput(s: Int, inCh: Int) = arcs(s)(inCh);

      def arcsFrom(s: Int, ch1: Int, ch2: Int) = arcs(s)(ch1)(ch2);

      def arcsFrom(s: Int) = arcs(s);
    }

  }


  implicit def asNormalAutomaton(a: Automaton): fst.Automaton[Double,Int,T] = new fst.Automaton[Double,Int,T] {
    import a._;
    val initialStateWeights = Map(a.initialState -> a.initialWeight);
    def finalWeight(s: Int) = a.finalWeight(s);
    def edgesMatching(s: Int, l: T) = {
      if(l == alphabet.sigma) {
        for {
          (idx,targets) <- a.arcsFrom(s)
          (sink,weight) <- targets.activeElements
        } yield new Arc(s,sink,index.get(idx),weight)
      } else {
        for {
          (sink,weight) <- a.arcsFrom(s)(index(l)).activeElements
        } yield new Arc(s,sink,l,weight)
      }
    };

    override lazy val allEdges = {
      for {
        s <- 0 until numStates iterator;
        (idx,targets) <- a.arcsFrom(s) iterator;
        (sink,weight) <- targets.activeElements
      } yield new Arc(s,sink,index.get(idx),weight)
    } toIndexedSeq

    override def allStates:Set[Int] = new Set[Int] {
      def iterator = (0 until numStates).iterator

      def -(elem: Int) = if(!contains(elem)) this else {
        BitSet.empty ++ (0 until numStates) - elem;
      }

      def +(elem: Int) = if(contains(elem)) this else {
        BitSet.empty ++ (0 until numStates) + elem;
      }

      def contains(elem: Int) = {
        0 <= elem && elem < numStates;
      }

      override def size = numStates;

      override def toIndexedSeq[B >: Int] = 0 until numStates;
    }

    override def collapseEdges = (a:fst.Automaton[Double,Int,T]);
    override def relabel = (a:fst.Automaton[Double,Int,T]);
    override def relabelWithIndex = (a:fst.Automaton[Double,Int,T],new DenseIntIndex(numStates));


  }

  protected def mkSparseVector(numStates: Int): SparseVector = {
     val sp = new SparseVector(numStates);
     sp.default = ring.zero;
     sp
   }

}
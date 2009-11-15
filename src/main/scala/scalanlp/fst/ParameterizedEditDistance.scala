package scalanlp.fst;

import scalanlp.math.Semiring.LogSpace.doubleIsLogSpace;
import scalanlp.math.Numerics._;

/**
 * Levhenstein transducer over sum of alignments (not viterbi
 * alignment) with the given parameters;
 *
 * The cost function must handle Epsilon, and Rho might be included 
 * in the alphabet, or not.
 *
 * When composed with an input transducer that is markovian, will
 * produce a markovian marginal.
 *
 * @author dlwh
 */
class ParameterizedEditDistance(costFunction: (Char,Char)=>Double, alphabet: Set[Char])
        extends Transducer[Double,Int,Char,Char]() {
  import Transducer._;

  val totalChars = alphabet.size;

  /**
   * total costs for each input character
   */
  private val inputNormalizer: Map[Char,Double] = {
    import scala.collection.breakOut
    alphabet.map { inCh => 
      val allCosts = alphabet.iterator.map( outCh => costFunction(inCh,outCh)).toSeq;
      val totalCost = logSum(allCosts);
      (inCh,totalCost);
    } (breakOut);
  }


  private val Eps = inAlpha.epsilon;
  private val Sigma = inAlpha.sigma;
  private val allChars = alphabet + Eps;

  val initialStateWeights = Map( 0 -> 0.0);
  
  // 1 - all insertions possible 
  private val theFinalWeight = {
    val edges = edgesMatching(0,Eps,Sigma).map(_.weight).toSeq;
    val totalInsertMass = logSum(edges);
    Math.log(1- Math.exp(totalInsertMass));
  }
  def finalWeight(s: Int) = theFinalWeight;

  override def allEdges:Seq[Arc] = (edgesMatching(0,inAlpha.sigma,outAlpha.sigma)).toSeq;

  def edgesMatching(s: Int, a: Char, b: Char) = {

    if(a == Sigma) {
      for {
        a <- allChars.iterator
      } yield {
        Arc(0,0,a,b, cost);
      }
    } else if(b == Sigma) {
      for(b <- allChars.iterator) yield
        Arc(0,0,a,b,costFunction(a,b));
    } else if(a == b && b == Eps) {
      Iterator.empty;
    } else { // Woot.
      Iterator.single(Arc(0,0,a,b,costFunction(a,b)));
    }
  }
}


package scalanlp.fst;

import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before


import scalanlp.math._;
import scala.collection.Traversable;
import scala.collection.Seq;
import scala.collection.mutable.ArrayBuffer;
import scala.collection.mutable.PriorityQueue;


@RunWith(classOf[JUnitRunner])
class BigramSemiringTest extends FunSuite {
  import BigramSemiring._;
  import ring._;
  test("bg zero + zero == zero") {
    assert(plus(zero,zero) === zero)
  }
  test("bg one + zero == one") {
    assert(plus(one,zero) === one)
  }
  test("bg zero + one == one") {
    assert(plus(zero,one) === one)
  }

  test("zero* == 1") {
    assert(closure(zero) === one);
  }

  test("simple* works") {
    import Math.log;
    val w = promoteOnlyWeight(log(1/2.));
    val cl = closure(w);
    assert(cl.totalProb == log(2.0));
    // closure * (1/2 * 1/2) * closure again
    assert(cl.counts('#','#') === 0.0);
  }


  test("constant automaton") {
    import Automaton._;
    import Semiring.LogSpace.doubleIsLogSpace;

    val auto = constant("Hello",0.0).reweight(promote[Int] _ , promoteOnlyWeight _);
    val counts = auto.cost.counts;

    assert( counts('#', 'H') === 0.0);
    assert( counts('H', 'e') === 0.0);
    assert( counts('l', 'l') === 0.0);
    assert( counts('l', 'o') === 0.0);
    assert( counts('o', '#') === 0.0);
    assert( counts('o').logTotal === 0.0);
    assert( (counts('l').logTotal - 0.6931471805599453).abs < 1E-6, counts('l'));
    assert( counts('e').logTotal === 0.0);
    assert( counts('H').logTotal === 0.0);
  }

  test("split automaton") {
    import Automaton._;
    import Semiring.LogSpace.doubleIsLogSpace;
    import IdentityPartitioner._;
    import Numerics._;

    val auto1 = constant("Hello",0.0);
    val auto2 = constant("Hemmo",0.0)
    val auto = Minimizer.minimize(auto1|auto2).reweight(promote[Int] _ , promoteOnlyWeight _);
    val counts = auto.cost.counts;

    assert( counts('#', 'H') === logSum(0.0,0.0));
    assert( counts('#', '#') === Double.NegativeInfinity);
    assert( counts('H', 'e') === logSum(0.0,0.0));
    assert( counts('l', 'l') === 0.0);
    assert( counts('m', 'o') === 0.0);
    assert( counts('o').logTotal === logSum(0.0,0.0));
    assert((counts('l').logTotal - 0.6931471805599453).abs < 1E-6, counts('l'));
    assert( counts('H').logTotal === logSum(0.0,0.0));
  }

  
}
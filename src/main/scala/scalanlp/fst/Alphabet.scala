package scalanlp.fst

abstract class Alphabet[@specialized("Char, Int") T] {
  val epsilon: T;
}

object Alphabet {
  implicit val zeroEpsCharBet = new Alphabet[Char] {
    val epsilon = '\0';
  }
}

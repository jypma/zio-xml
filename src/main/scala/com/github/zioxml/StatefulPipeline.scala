package com.github.zioxml

import zio.Ref
import zio.Chunk
import zio.stream.ZPipeline

object StatefulPipeline {
  /** Convenience method to only specify the input type of the stateful transducer, and letting type inference
    *  figure out the rest. */
  def of[I] = new StatefulPipelineOf[I]

  class StatefulPipelineOf[I] {
    def apply[S,O]
      (init: =>S)
      (process: (S,I) => (S, Chunk[O]))
      (reset: S => Chunk[O]) = make[S,I,O](init)(process)(reset)
  }

  /** Creates a pipeline that has state [S], initialized by [init]. Every input element [I] is allowed to both
    *  update state S to a new value, and output a chunk of elements O (which can be empty).  On transducer
    *  reset (end-of-stream), [reset] is evaluated and outputted. */
  def make[S,I,O]
    (init: =>S)
    (process: (S,I) => (S, Chunk[O]))
    (reset: S => Chunk[O]): ZPipeline[Any, Nothing, I, O] = {
    ZPipeline.fromPush {
      for {
        stateRef <- Ref.make(init)
      } yield {
        (optChunk: Option[Chunk[I]]) => optChunk match {
          case None =>
            // Reset state
            for {
              state <- stateRef.get
              out = reset(state)
              _ <- stateRef.set(init)
            } yield out

          case Some(chunk) =>
            for {
              state <- stateRef.get
              (newState, out) = chunk.foldLeft((state, Chunk[O]())) {
                case ((s, o), i) =>
                  val (newState, out) = process(s, i)
                  (newState, o ++ out)
              }
              _ <- stateRef.set(newState)
            } yield out
        }
      }
    }
  }
}

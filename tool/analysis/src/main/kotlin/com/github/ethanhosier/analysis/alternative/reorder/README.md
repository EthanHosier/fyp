# Reorder synthesis

Enumerates alternative valid orderings of a window of refactoring specs,
then synthesises the ones whose end-state is AST-equivalent to the user's.

## Dependency rules

Spec A must run before spec B iff their effects collide on the same
`Entity` under any of these patterns:

| A's effect  | B's effect  | Why                                                   |
| ----------- | ----------- | ----------------------------------------------------- |
| produces X  | reads X     | B needs X to exist                                    |
| produces X  | consumes X  | can't consume what doesn't exist yet                  |
| produces X  | writes X    | can't mutate what doesn't exist yet                   |
| writes X    | reads X     | B reads the post-mutation state                       |
| writes X    | writes X    | WAW — serialise to a deterministic final state        |
| writes X    | consumes X  | mutation must land before removal                     |
| reads X     | consumes X  | B's removal would invalidate A's read                 |
| reads X     | writes X    | A's read must see pre-mutation state                  |
| consumes X  | produces X  | re-create the same identity after destroying it       |

Symmetric collisions (`produces`/`produces`, `consumes`/`consumes`) are
conflicts with no defined order — those orderings are invalid, not just
constrained.

Two specs **commute** (no edge in either direction) when their
effect-sets are disjoint on every entity, OR they only overlap on shared
`reads` (RAR — both read, neither mutates).

SSA `version` on named entities lets the analyzer distinguish "the `foo`
before rename" from "the `foo` after rename", so a later op on the
renamed identity correctly chains behind the rename instead of looking
like an independent op on a different entity.
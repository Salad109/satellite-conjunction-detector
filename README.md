todo:
calculate conjunctions

optional:
streaming ingestion

## Conjunction Candidate Reduction

| Strategy                        | Unique Pairs | Reduction vs Full Set |
|---------------------------------|-------------:|----------------------:|
| Full set (32,641 satellites)    |  532,701,120 |                     - |
| Skip debris on debris           |  454,832,160 |                 85,4% |
| Only overlapping apogee/perigee |   99,450,032 |                 18,7% |
| Plane intersection              |   25,216,090 |                 4,73% |
| All strategies sequentially     |   17,797,883 |                 3,70% |

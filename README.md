# chrondb

Understand how and when changes were made. **chrondb** stores all history, and lets you query against any point in time.

Git structure is a powerful solution for storing **"data"** (files) in chronological order, _chrondb_ uses git core as a data structure to structure the data timeline, making it possible to return to any necessary point and bringing all git functions for a database:

- diff
- notes
- restore
- branch
- checkout
- revert
- merge
- log
- blame
- archive
- ... [git high-level commands (porcelain)](https://git-scm.com/docs/git#_high_level_commands_porcelain)

## POC stdout

```
"4"
"[{\"number\":\"1\",\"title\":\"Please Please Me\"},{\"number\":\"2\",\"title\":\"With the Beatles\"},{\"number\":\"3\",\"title\":\"A Hard Day's Night\"},{\"number\":\"4\",\"title\":\"Beatles for Sale\"},{\"number\":\"5\",\"title\":\"Help!\"}]"
"data/0d345e56-09b1-47b2-8840-fdb2095c1d23"
{:added #{"flubber.json"},
 :changed #{},
 :missing #{},
 :modified #{},
 :removed #{},
 :untracked #{}}
{:added #{},
 :changed #{},
 :missing #{},
 :modified #{},
 :removed #{},
 :untracked #{}}
[{:number "2", :title "With the Beatles"}
 {:number "4", :title "Beatles for Sale"}]
```

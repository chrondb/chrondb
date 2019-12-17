# chrondb

_Chronological key/value Database storing based on database-shaped git (core) architecture_

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
- [hooks](https://git-scm.com/docs/githooks#_hooks)
- ... [git high-level commands (porcelain)](https://git-scm.com/docs/git#_high_level_commands_porcelain)

## Features

- **Historic change** with chronological evolution - _aka_ git commits
- Document - schemeless
- Search - full-text search
- Sorting by any field
- Flexible faceting, highlighting, joins and result grouping
- Cross-Platform Solution
- Transactions (git branch/merge)
- Data replication (git hooks events)
- Cluster
- High availability
- Plugable/Expandable (possibility of integration with tools that connects in git repository, e.g. jenkins)

## Problems

- Large volume of writing


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

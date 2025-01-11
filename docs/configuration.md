# ChronDB Configuration

This document details all configuration options available in ChronDB.

## Configuration File

ChronDB uses an EDN (Extensible Data Notation) configuration file. By default, it looks for a `config.edn` file in the project's root directory.

## Configuration Sections

### 1. Git (:git)

Git storage related configurations:

```clojure
:git {
  :committer-name "ChronDB"      ; Name that will appear in commits
  :committer-email "chrondb@example.com"  ; Email that will appear in commits
  :default-branch "main"         ; Main repository branch
  :sign-commits false            ; GPG commit signing
}
```

#### Details:
- `committer-name`: Name used to identify the commit author
- `committer-email`: Email used to identify the commit author
- `default-branch`: Name of the main branch where changes will be saved
- `sign-commits`: If true, commits will be signed with GPG (requires additional setup)

### 2. Storage (:storage)

Data storage related configurations:

```clojure
:storage {
  :data-dir "data"              ; Base storage directory
}
```

#### Details:
- `data-dir`: Directory where documents will be stored
  - Documents are saved in JSON format
  - Directory structure is maintained by Git
  - Absolute paths are recommended in production

### 3. Logging (:logging)

Logging system configurations:

```clojure
:logging {
  :level :info                  ; Minimum log level
  :output :stdout               ; Where logs will be written
  :file "chrondb.log"          ; Log file (if output = :file)
}
```

#### Details:

##### Log Levels:
- `:debug` - Detailed information useful for development
  - Detailed Git operations
  - Repository status
  - Indexing details
- `:info` - General operation information
  - Document creation/deletion
  - Search operations
  - Component initialization
- `:warn` - Warnings about non-ideal situations
  - Attempts to access non-existent documents
  - Performance issues
- `:error` - Errors affecting functionality
  - Git operation failures
  - Indexing problems
  - I/O errors

##### Log Outputs:
- `:stdout` - Logs are written to standard output
- `:file` - Logs are written to a file
  - File is created if it doesn't exist
  - Log rotation is not handled automatically

## Complete Example

```clojure
{:git {:committer-name "ChronDB"
       :committer-email "chrondb@example.com"
       :default-branch "main"
       :sign-commits false}
 :storage {:data-dir "/var/lib/chrondb/data"}
 :logging {:level :info
           :output :file
           :file "/var/log/chrondb/chrondb.log"}}
```

## Loading Configuration

```clojure
(require '[chrondb.config :as config])

;; Load from default file (config.edn)
(def cfg (config/load-config))

;; Or specify a file
(def cfg (config/load-config "/etc/chrondb/prod.edn"))

;; Or merge with default settings
(def cfg (config/load-config {:logging {:level :debug}}))
```

## Best Practices

1. **Development Environment**:
   - Use `:debug` as log level
   - Set `:output` to `:stdout`
   - Use relative paths for easier development

2. **Production Environment**:
   - Use `:info` or `:warn` as log level
   - Set `:output` to `:file`
   - Use absolute paths for directories
   - Configure external log rotation

3. **Security**:
   - Don't version control `config.edn` with credentials
   - Use environment variables for sensitive information
   - Restrict configuration file permissions 
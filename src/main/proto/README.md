Why this folder structure?
==========================
```
└── github.com
    └── grandcat
        └── flexsmc
            └── proto
                ├── job
                │   └── job.proto
                └── smc
                    └── smc.proto
```
The folder structure mimics the typical structure for Go-lang projects. In this case,
for the associated `flexsmc` project.

The benefit:
we can reuse all `.proto` files and especially its import paths. So no rewrite
is necessary to run protobuf compilers for both Java and Go.% 

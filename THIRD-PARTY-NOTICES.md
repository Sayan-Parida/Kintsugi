# Third-party notices

The Maven Wrapper 3.3.2 scripts (`mvnw`, `mvnw.cmd`) and wrapper configuration
retain their original Apache Software Foundation headers. The applicable
[Apache-2.0 license](licenses/Apache-2.0.txt) and upstream [NOTICE](NOTICE) are included.
Source provenance is [Apache Maven Wrapper 3.3.2](https://github.com/apache/maven-wrapper/tree/maven-wrapper-3.3.2).

Shirudo is an external upstream project. Its shields and source checkout are
obtained separately under their upstream licenses. Kintsugi's documentation
links to the reviewed upstream files. The repository includes no Shirudo shield
JAR or source copy. Preserve upstream notices when distributing those artifacts.

The fixture resolves H2 2.3.232 through Maven and packages it in the generated
fixture JAR. H2's embedded license resources must be retained in that JAR.
Build outputs are excluded from this source push. Spring Boot and test libraries
are likewise resolved through Maven under their own licenses.

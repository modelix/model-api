# model-api

Modelix Model API, used by [Modelix](https://github.com/modelix/modelix) and Shadow Models (part of [MPS extensions]
(https://github.com/JetBrains/MPS-extensions)).

Model API was previously part of MPS extensions as well but was extracted into a separate project so that it can support
multiple versions of MPS.

# Versioning

Model API was extracted from MPS-extensions master as of 2022-03-17. The minor version at that point was 2021.2.
The releases of the extracted project will begin with version 2021.3 and will follow semantic versioning.

Semantic versioning is considered from the point of view of _API consumers_, not _API implementors_. Adding a new 
method to the API is a minor change because it is backwards compatible from the point of view of API consumers, even 
though it breaks the API implementors who now have to implement the method.

Folder used as a local Maven repository to keep 3rd party libraries otherwise not available in the public repositories.
Use the following command to install new artifact:

`mvn deploy:deploy-file -DgroupId="XXX" -DartifactId="YYY" -Dversion="A.B.C" -Durl=file:"./local-maven-repo/" -DrepositoryId=local-maven-repo -DupdateReleaseInfo=true -Dfile="path to your file"`

Do not forget to delete obsolete versions and artifacts.  

Use only in exceptional cases, when there is no public repo!
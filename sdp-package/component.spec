%undefine _missing_build_ids_terminate_build
%define debug_package %{nil}

%define spec_stack_version  %( echo "%{stack_version}" | sed "s/[.]/_/g" )_%{build_id}
%define name                ozone_%{spec_stack_version}
%define spec_stack_home     /usr/%{stack_name}/%{stack_version}-%{build_id}

# Even though we split the RPM into arch and noarch, it still will build and install
# the entirety of ozone. Defining this tells RPM not to fail the build
# when it notices that we didn't package most of the installed files.
%define _unpackaged_files_terminate_build 0

# RPM searches perl files for dependancies and this breaks for non packaged perl lib
# like thrift so disable this
%define _use_internal_dependency_generator 0

Name: %{name}
Version: %{component_version}.%{stack_version}
Release: %{build_id}
Summary: Ozone is a scalable, redundant, and distributed object store for Hadoop and Cloud-native environments.
License: ASL 2.0
URL: https://ozone.apache.org/
Group: Development/Libraries
# Sadly, Sun/Oracle JDK in RPM form doesn't provide libjvm.so, which means we have
# to set AutoReq to no in order to minimize confusion. Not ideal, but seems to work.
# I wish there was a way to disable just one auto dependency (libjvm.so)
AutoReq: no

%description
Ozone is a scalable, redundant, and distributed object store for Hadoop and Cloud-native environments.

%package client
Summary: Apache Ozone Client
Group: System/Daemons

%description client
Client for Apache Ozone

%files
%defattr(-,root,root)
%{spec_stack_home}/ozone/bin/*
%{spec_stack_home}/ozone/sbin/*
%{spec_stack_home}/ozone/lib/*
%{spec_stack_home}/ozone/libexec/*
%{spec_stack_home}/ozone/share/*
%{spec_stack_home}/ozone/etc/*

%files client
%defattr(-,root,root)
%dir %{spec_stack_home}/ozone
%{spec_stack_home}/ozone/share/ozone/lib/ozone-filesystem-hadoop3-%{component_version}.jar

%clean
# in order to provide build rpms without the whole workspace clean and lost the previous mvn build stage
%__rm -rf $RPM_BUILD_ROOT

# Service Extractor

Extract SOAP transactions from SSL dump.

## Build and Run

To build an executable package that includes all the binaries, use:

    mvn assembly:assembly

To run the extraction, transfer target/txn-extractor-bin.zip to the host where the extraction will be performed, and
unzip the distribution. Cd into the distribution directory, and add the distribution directory to your path, e.g.

    export PATH=$PATH:`pwd`

At this point you have two options. Once would be to process all the files without writing them to the database,
the other being persisting the transactions. Before processing a large batch, it can be useful to verify the
files to be processed do not contain any variations to form or content that have not been accounted for in the
program. Processing without persisting the data is done by not setting the environment variables for persistence.

Persistence of the transactions requires exporting three environment variables that specify the database user, the
password, and the database URL, eg.

    export EXTRACTOR_USER=dbusername
    export EXTRACTOR_PASSWORD=supertopsecret
    export EXTRACTOR_URL=jdbc:oracle:thin:@//dbhostname:listenerPort/OracleServiceName

Note if your database uses a SID instead of a service name the URL format will differ from the above.

Make sure to clean out the target database before importing, e.g.

    truncate table service_call_dump

Obviously if this is the first time you are processing dump files you should create the destination table
using https://github.com/d-smith/service-extractor/blob/master/src/main/tabledef.sql

At this point you are ready to process the files.

    ./extract-from-files.sh /path/to/files/*

## Implementation Notes

Some things to note on extracting transactions from the network dump:

First, you have to look at the first two columns to detect when a new
connection (and thus timestamp) appears in the file. You cannot just
grab the next line after seeing "New TCP Connection" because the lines
get interleaved.

For example, the file might contain:

    New TCP connection #21: 10.93.176.145(35931) <-> 10.93.202.30(11001)
    New TCP connection #22: 10.93.176.145(35932) <-> 10.93.202.30(11001)
    21 1  1391605202.0925 (0.0023)  C>S V3.1(157)  Handshake
          ClientHello
                  Version 3.1 

The service name is extracted from the SOAP request. We could grab it from the SOAPAction head, but that is only set
for B2B transactions: web service transactions don't set this header. In general this note won't make any sense outside
of our application domain,

In the output, application data doesn't always have a dash line preceeding it, e.g.

    72 17 1391605207.9357 (0.0371)  S>C V3.1(739)  application_data
    New TCP connection #73: 10.33.151.39(53843) <-> 10.93.202.30(11001)
    73 1  1391605207.9391 (0.0311)  C>S V3.1(157)  Handshake

Note that some soap envelopes can contain empty lines in the middle of the request, for example in notes. Thus we have
to look for the end of the soap envelope instead of assuming termination from a blank line.

When putting together chunked reponses, an empty line is not enough to denote the last line - need to find the
0 length identifier.

    ---------------------------------------------------------------
    102 19 1391605211.7422 (0.0000)  S>C V3.1(22)  application_data
    ---------------------------------------------------------------

    ---------------------------------------------------------------
    102 20 1391605211.7422 (0.0000)  S>C V3.1(25)  application_data
    ---------------------------------------------------------------
    0

Also note data gets interleaved and isn't always proceeded by a chunk size...

Some things to note:

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

The service name is extracted from the SOAP request. We could grab it from the SOAPAction head, but that is only set for B2B transactions: web service transations don't set this header.

Also application data doesn't always have a dash line, e.g.

    72 17 1391605207.9357 (0.0371)  S>C V3.1(739)  application_data
    New TCP connection #73: 10.33.151.39(53843) <-> 10.93.202.30(11001)
    73 1  1391605207.9391 (0.0311)  C>S V3.1(157)  Handshake



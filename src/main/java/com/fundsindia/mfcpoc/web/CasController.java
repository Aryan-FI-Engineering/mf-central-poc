package com.fundsindia.mfcpoc.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fundsindia.mfcpoc.cas.CasRequest;
import com.fundsindia.mfcpoc.cas.CasRequestStore;
import com.fundsindia.mfcpoc.cas.CasStatementCommand;
import com.fundsindia.mfcpoc.cas.CasStatementService;
import com.fundsindia.mfcpoc.cas.CasStatementService.StartedCasRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cas-requests")
class CasController {

    private final CasStatementService casStatements;
    private final CasRequestStore store;

    CasController(CasStatementService casStatements, CasRequestStore store) {
        this.casStatements = casStatements;
        this.store = store;
    }

    @PostMapping
    StartedCasRequest start(@RequestBody CasStatementCommand command) {
        return casStatements.start(command);
    }

    @GetMapping
    List<CasRequest> list(@RequestParam(defaultValue = "AWAITING_QR") CasRequest.Status status) {
        return store.withStatus(status);
    }

    @PostMapping(path = "/{clientRefNo}/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    JsonNode uploadQr(@PathVariable String clientRefNo, @RequestParam("file") MultipartFile file) throws IOException {
        return casStatements.fetchStatement(clientRefNo, file.getBytes());
    }
}

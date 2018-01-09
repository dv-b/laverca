/* ==========================================
 * Laverca Project
 * https://sourceforge.net/projects/laverca/
 * ==========================================
 * Copyright 2015 Laverca Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fi.laverca.mss;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import fi.laverca.CmsSignature;
import fi.laverca.MSS_Formats;
import fi.laverca.Pkcs1;
import fi.laverca.Signature;
import fi.laverca.etsi.EtsiResponse;
import fi.laverca.jaxb.mss.MSSSignatureReq;
import fi.laverca.jaxb.mss.MSSSignatureResp;
import fi.laverca.jaxb.mss.MSSStatusResp;
import fi.laverca.jaxb.mss.MessagingModeType;
import fi.laverca.jaxb.mss.SignatureType;
import fi.laverca.jaxb.mss.StatusDetailType;
import fi.laverca.jaxb.mss.StatusType;
import fi.laverca.jaxb.mssfi.PKCS1;

/**
 * Represents the final response to a signature request. Signature requests can be either synchronous or asynchronous. 
 * 
 * <p>
 * In synchronous mode, the only response is a signature response, which <b>contains</b> the signature (unless the signature is unsuccessful),
 * and status details.   
 * 
 * <p>
 * In asynchronous mode, the first response is a signature response that does <b>not</b> contain a signature. After receiving the signature response,
 * Laverca sends periodical status requests. The final status response will contain a signature (if successful), and status details. 
 * 
 * <p>
 * The signature and status details in synchronous signature response and asynchronous status response are identical. 
 * 
 */
public abstract class MssResponse {

    private static final Log log = LogFactory.getLog(EtsiResponse.class);
    
    // These will be made private in a future release, so use utility methods if possible.
    /**
     * @deprecated Will be made private in the future.
     */
    @Deprecated
    public final MSSSignatureReq  originalSigReq;
    /**
     * @deprecated WIll be made private in the future. Use {@link MssResponse#getMSS_SignatureResp()} instead.
     */
    @Deprecated
    public final MSSSignatureResp originalSigResp;
    /**
     * @deprecated WIll be made private in the future. Use {@link MssResponse#getMSS_StatusResp()} instead.
     */
    @Deprecated
    public final MSSStatusResp    finalStatusResp;
    
    private final StatusType    status;
    private final SignatureType signature;
    
    public MssResponse(final MSSSignatureReq  originalSigReq,
                       final MSSSignatureResp originalSigResp,
                       final MSSStatusResp    finalStatusResp) { 
        this.originalSigReq  = originalSigReq;
        this.originalSigResp = originalSigResp;
        this.finalStatusResp = finalStatusResp;
        
        this.originalSigResp.getMSSSignature();
        
        if (this.finalStatusResp != null) {
            this.status    = this.finalStatusResp.getStatus();
            this.signature = this.finalStatusResp.getMSSSignature();
        } else {
            this.status    = this.originalSigResp.getStatus();
            this.signature = this.originalSigResp.getMSSSignature();
        }
        
    }
    
    /** 
     * Get the MSS signature
     * 
     * @return signature or null if the response contains no signature.
     * @throws IllegalStateException if the signature parsing fails
     */
    public Signature getSignature() throws IllegalStateException {
        
        if (this.signature == null) {
            return null;
        }
        
        Signature signature = null;

        try {
            PKCS1  p1  = this.signature.getPKCS1();
            byte[] sig = this.signature.getBase64Signature();
            
            if (p1 != null) {
                signature = new Pkcs1(p1);
            }
            
            if (sig != null) {
                if (MSS_Formats.KIURU_PKCS1.equals(this.getMssFormat())) {
                    p1 = new PKCS1();
                    p1.setSignatureValue(sig);
                    signature = new Pkcs1(p1);
                } else {
                    signature = new CmsSignature(sig);
                } 
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        
        return signature;
    }
    
    /**
     * Check if the response has signature available
     * 
     * @return true if signature is available
     * @throws IllegalStateException if the signature parsing fails
     */
    public boolean hasSignature() throws IllegalStateException {
        return this.getSignature() != null;
    }

    /** 
     * Get the PKCS7 signature from this response
     * @return PKCS7 signature or null if the signature is not in this format.
     * @deprecated Use {@link #getSignature()} instead
     */
    @Deprecated
    public CmsSignature getPkcs7Signature() {
        try {
            return new CmsSignature(this.signature.getBase64Signature());
        } catch(IllegalArgumentException iae) {
            log.debug("not a pkcs7?", iae);
            return null;
        }

    }
    
    /** 
     * Get the PKCS1 signature from this response
     * @return PKCS1 signature or null if the signature is not in this format.
     * @deprecated Use {@link #getSignature()} instead
     */
    @Deprecated
    public Pkcs1 getPkcs1Signature() {
        try {
            return new Pkcs1(this.signature.getPKCS1());
        } catch(IllegalArgumentException iae) {
            log.debug("not a pkcs1?", iae);
            return null;
        }
    }

    /**
     * Get the raw XML datatype of the MSS_SignatureResp
     * @return MSS_SignatureResp
     */
    public MSSSignatureResp getMSS_SignatureResp() {
        return this.originalSigResp;
    }
    
    /** 
     * Get the raw XML datatype of the last MSS_StatusResp message.
     * <p>This is null if the request is synchronous. 
     * @return MSS_StatusResp
     */
    public MSSStatusResp getMSS_StatusResp() {
        return this.finalStatusResp;
    }

    /**
     * Get the latest status code of the transaction
     * @return latest status code or -1 if not available
     */
    public long getStatusCode() {
        if (this.status != null && this.status.getStatusCode() != null && this.status.getStatusCode().getValue() != null) {
            this.status.getStatusCode().getValue().longValue();
        }
        
        return -1;
    }
    
    /**
     * Get the latest status message of the transaction
     * @return latest status code or null if not available
     */
    public String getStatusMessage() {
        return this.status != null ? this.status.getStatusMessage() : null;
    }
    
    /**
     * Get the requested MSS_Format as String
     * @return requested MSS_Format as String. May be null.
     */
    public String getMssFormat() {
        if (this.originalSigReq != null &&
            this.originalSigReq.getMSSFormat() != null) {
            
            return this.originalSigReq.getMSSFormat().getMssURI();
        }
        return null;
    }
    
    /**
     * Get the status detail type of the latest response. Works in both synchronous and asynchronous messaging modes. 
     * @return Status detail type of the signature. Can be null. 
     */
    public StatusDetailType getStatusDetail() {
        return this.status != null ? this.status.getStatusDetail() : null;
    }
    
    /**
     * Was this transaction synchronous?
     * @return true if MessagingMode was "synch"
     */
    public boolean isSynchronous() {
        if (this.originalSigReq == null) return false;
        return MessagingModeType.SYNCH == this.originalSigReq.getMessagingMode();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName());
        sb.append(" [");
        sb.append("StatusCode=").append(this.getStatusCode());
        sb.append(", StatusMessage=").append(this.getStatusMessage());
        sb.append(", HasSignature=").append(this.hasSignature());
        sb.append("]");
        return sb.toString();
    }
    
}

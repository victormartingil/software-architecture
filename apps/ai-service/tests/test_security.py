import json
import time
import httpx
import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from ai_service.infrastructure.security import JwtVerifier
from ai_service.domain import ServiceError


@pytest.fixture
def signing():
    private = rsa.generate_private_key(public_exponent=65537,key_size=2048)
    jwk = json.loads(jwt.algorithms.RSAAlgorithm.to_jwk(private.public_key()))
    jwk.update(kid='one',alg='RS256',use='sig')
    return private,jwk


def make_token(key, **overrides):
    claims = {'sub':'alice','tenant':'demo','iss':'http://issuer','aud':'reference-api','exp':int(time.time())+100,
              'realm_access':{'roles':['CUSTOMER']}}
    claims.update(overrides)
    return jwt.encode(claims,key,algorithm='RS256',headers={'kid':'one'})


async def test_signed_jwks_validation_and_negatives(signing):
    private,jwk = signing
    async with httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(200,json={'keys':[jwk]}))) as client:
        verifier = JwtVerifier(client,'http://issuer','reference-api','http://jwks')
        assert (await verifier.verify(make_token(private))).tenant == 'demo'
        for overrides in [{'iss':'wrong'}, {'aud':'wrong'}, {'exp':1}, {'tenant':None}, {'sub':' '}, {'realm_access':None}, {'realm_access':'invalid'}]:
            with pytest.raises(ServiceError) as e: await verifier.verify(make_token(private,**overrides))
            assert e.value.status == 401
        no_exp = {'sub':'alice','tenant':'demo','iss':'http://issuer','aud':'reference-api'}
        with pytest.raises(ServiceError):
            await verifier.verify(jwt.encode(no_exp,private,algorithm='RS256',headers={'kid':'one'}))
        other_key = rsa.generate_private_key(public_exponent=65537,key_size=2048)
        with pytest.raises(ServiceError): await verifier.verify(make_token(other_key))
        with pytest.raises(ServiceError) as e:
            await verifier.verify(make_token(private,realm_access={'roles':[]}))
        assert e.value.status == 403


async def test_jwks_encryption_key_does_not_break_rs256_signing_key(signing):
    private, signing_jwk = signing
    encryption_jwk = {**signing_jwk,'kid':'encryption','use':'enc','alg':'RSA-OAEP'}
    jwks = {'keys':[encryption_jwk,signing_jwk]}
    async with httpx.AsyncClient(transport=httpx.MockTransport(lambda r:httpx.Response(200,json=jwks))) as client:
        verifier = JwtVerifier(client,'http://issuer','reference-api','http://jwks')
        assert (await verifier.verify(make_token(private))).subject == 'alice'
        assert set(verifier.keys) == {'one'}
        forged_header = jwt.encode({'sub':'alice','tenant':'demo','iss':'http://issuer',
            'aud':'reference-api','exp':int(time.time())+100,'realm_access':{'roles':['CUSTOMER']}},
            private,algorithm='RS256',headers={'kid':'encryption'})
        with pytest.raises(ServiceError,match='Unknown signing key'):
            await verifier.verify(forged_header)

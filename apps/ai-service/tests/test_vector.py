import os
from pathlib import Path
import psycopg
import pytest
from ai_service.infrastructure.retrieval import VectorRetriever


@pytest.mark.skipif(not os.environ.get('TEST_VECTOR_DSN'),reason='requires dedicated pgvector database')
async def test_real_pgvector_filters_before_distance_and_validates_dimension():
    dsn = os.environ['TEST_VECTOR_DSN']
    async with await psycopg.AsyncConnection.connect(dsn) as c:
        await c.execute((Path(__file__).parents[1]/'data/vector-schema.sql').read_text())
        await c.execute('TRUNCATE ai_documents')
        for values in [('own-near','demo','Allowed near',3,'[1,0,0]'),('own-far','demo','Allowed far',3,'[0,1,0]'),
                       ('secret','other','Must never be returned',3,'[1,0,0]'),('other-dimension','demo','Wrong dimension',2,'[1,0]')]:
            await c.execute('INSERT INTO ai_documents VALUES (%s,%s,%s,%s,%s::vector)',values)
    async def embedding(q): return [1.,0.,0.]
    docs = await VectorRetriever(dsn,embedding,3).search('demo','query')
    assert [d.id for d in docs] == ['own-near','own-far']
    async with await psycopg.AsyncConnection.connect(dsn) as c:
        with pytest.raises(psycopg.errors.CheckViolation):
            await c.execute("INSERT INTO ai_documents VALUES ('bad','demo','bad',3,'[1,0]'::vector)")

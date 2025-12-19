from fastapi import FastAPI, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
import logging
import os
from sqlalchemy import create_engine, Column, Integer, String
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("film_api")

# Database setup
DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://films_user:films_password@db:5432/films_db")

engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

# Database model
class FilmDB(Base):
    __tablename__ = "films"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String, nullable=False)
    year = Column(Integer, nullable=True)
    director = Column(String, nullable=True)
    status = Column(String, nullable=False)
    dateAdded = Column(String, nullable=False)
    note = Column(String, nullable=False)

# Create tables
Base.metadata.create_all(bind=engine)

# Dependency to get DB session
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Pydantic models
class FilmCreate(BaseModel):
    title: str
    year: Optional[int] = None
    director: Optional[str] = None
    status: str
    dateAdded: str
    note: str

class FilmResponse(BaseModel):
    id: int
    title: str
    year: Optional[int] = None
    director: Optional[str] = None
    status: str
    dateAdded: str
    note: str

    class Config:
        from_attributes = True

class FilmUpdate(BaseModel):
    title: str
    year: Optional[int] = None
    director: Optional[str] = None
    status: str
    dateAdded: str
    note: str

@app.get("/films", response_model=List[FilmResponse])
def get_films(db: Session = Depends(get_db)):
    films = db.query(FilmDB).all()
    logger.info(f"GET /films count={len(films)}")
    return films

@app.post("/films", response_model=FilmResponse)
def create_film(film: FilmCreate, db: Session = Depends(get_db)):
    logger.info(f"POST /films payload={film.model_dump_json()}")
    
    db_film = FilmDB(
        title=film.title,
        year=film.year,
        director=film.director,
        status=film.status,
        dateAdded=film.dateAdded,
        note=film.note
    )
    db.add(db_film)
    db.commit()
    db.refresh(db_film)
    
    logger.info(f"Created film id={db_film.id}")
    return db_film

@app.get("/films/{film_id}", response_model=FilmResponse)
def get_film(film_id: int, db: Session = Depends(get_db)):
    logger.info(f"GET /films/{film_id}")
    film = db.query(FilmDB).filter(FilmDB.id == film_id).first()
    if not film:
        raise HTTPException(status_code=404, detail="Film not found")
    return film

@app.put("/films/{film_id}", response_model=FilmResponse)
def update_film(film_id: int, film: FilmUpdate, db: Session = Depends(get_db)):
    logger.info(f"PUT /films/{film_id} payload={film.model_dump_json()}")
    
    db_film = db.query(FilmDB).filter(FilmDB.id == film_id).first()
    if not db_film:
        raise HTTPException(status_code=404, detail="Film not found")
    
    db_film.title = film.title
    db_film.year = film.year
    db_film.director = film.director
    db_film.status = film.status
    db_film.dateAdded = film.dateAdded
    db_film.note = film.note
    
    db.commit()
    db.refresh(db_film)
    
    logger.info(f"Updated film id={film_id}")
    return db_film

@app.delete("/films/{film_id}")
def delete_film(film_id: int, db: Session = Depends(get_db)):
    logger.info(f"DELETE /films/{film_id}")
    
    db_film = db.query(FilmDB).filter(FilmDB.id == film_id).first()
    if not db_film:
        raise HTTPException(status_code=404, detail="Film not found")
    
    db.delete(db_film)
    db.commit()
    
    total_count = db.query(FilmDB).count()
    logger.info(f"Deleted film id={film_id} total={total_count}")
    return {"message": "Film deleted"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5001)

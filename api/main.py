from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("film_api")

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

films = []
next_id = 1

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

class FilmUpdate(BaseModel):
    title: str
    year: Optional[int] = None
    director: Optional[str] = None
    status: str
    dateAdded: str
    note: str

@app.get("/films", response_model=List[FilmResponse])
def get_films():
    logger.info(f"GET /films count={len(films)}")
    return films

@app.post("/films", response_model=FilmResponse)
def create_film(film: FilmCreate):
    global next_id
    logger.info(f"POST /films payload={film.model_dump_json()}")
    new_film = FilmResponse(
        id=next_id,
        title=film.title,
        year=film.year,
        director=film.director,
        status=film.status,
        dateAdded=film.dateAdded,
        note=film.note
    )
    films.append(new_film)
    next_id += 1
    logger.info(f"Created film id={new_film.id} total={len(films)}")
    return new_film

@app.get("/films/{film_id}", response_model=FilmResponse)
def get_film(film_id: int):
    logger.info(f"GET /films/{film_id}")
    for film in films:
        if film.id == film_id:
            return film
    raise HTTPException(status_code=404, detail="Film not found")

@app.put("/films/{film_id}", response_model=FilmResponse)
def update_film(film_id: int, film: FilmUpdate):
    logger.info(f"PUT /films/{film_id} payload={film.model_dump_json()}")
    for i, f in enumerate(films):
        if f.id == film_id:
            updated_film = FilmResponse(
                id=film_id,
                title=film.title,
                year=film.year,
                director=film.director,
                status=film.status,
                dateAdded=film.dateAdded,
                note=film.note
            )
            films[i] = updated_film
            logger.info(f"Updated film id={film_id}")
            return updated_film
    raise HTTPException(status_code=404, detail="Film not found")

@app.delete("/films/{film_id}")
def delete_film(film_id: int):
    logger.info(f"DELETE /films/{film_id}")
    for i, film in enumerate(films):
        if film.id == film_id:
            films.pop(i)
            logger.info(f"Deleted film id={film_id} total={len(films)}")
            return {"message": "Film deleted"}
    raise HTTPException(status_code=404, detail="Film not found")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5001)


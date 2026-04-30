from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
from src.inference import STSInferenceEngine

app = FastAPI(title="Slay the Spire AI Playspire Integration", description="AI Playspire Card/Shop Evaluator")

# Global Inference Engine Instance
# Ideally, we would load the trained weights here: engine = STSInferenceEngine('model.pth')
engine = STSInferenceEngine()

class PlayerState(BaseModel):
    deck: List[str]
    relics: List[str]
    hp: int
    max_hp: int
    gold: int
    floor: int
    ascension: int
    
class Choice(BaseModel):
    action: str
    target: Optional[str] = None
    cost: Optional[int] = 0

class RecommendationRequest(BaseModel):
    state: PlayerState
    choices: List[Choice]
    
class ShopRequest(BaseModel):
    state: PlayerState
    goods: List[Choice]

@app.post("/recommend/choice")
def recommend_choice(req: RecommendationRequest):
    """
    Evaluates multiple choices and returns the one that maximizes survival probability.
    E.g., Card Rewards, Event choices.
    """
    state_dict = req.state.dict()
    choices_dict = [c.dict() for c in req.choices]
    
    if not choices_dict:
        raise HTTPException(status_code=400, detail="Choices list cannot be empty.")
        
    best_choice = engine.recommend_choice(state_dict, choices_dict)
    
    return {
        "status": "success",
        "best_choice": best_choice
    }
    
@app.post("/recommend/shop")
def recommend_shop(req: ShopRequest):
    """
    Evaluates a shop's inventory and greedy-algorithm returns a list of items to buy.
    """
    state_dict = req.state.dict()
    goods_dict = [g.dict() for g in req.goods]
    
    if not goods_dict:
        return {"status": "success", "buy_list": []}
        
    buy_list = engine.shop_greedy_search(state_dict, goods_dict)
    
    return {
        "status": "success",
        "buy_list": buy_list
    }

@app.get("/health")
def health_check():
    return {"status": "ok", "model": "STSValueNetwork"}

# To run: uvicorn src.api:app --reload
